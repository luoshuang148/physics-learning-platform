#!/usr/bin/env python3
"""Destructive fixture checks for an explicitly isolated MySQL instance only."""
import argparse, json, os, pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['upgrade','backup']);args=parser.parse_args()
if os.environ.get('CONFIRM_ISOLATED_DATABASE')!='1':
    raise SystemExit('Requires CONFIRM_ISOLATED_DATABASE=1; never point at a business database.')
container=os.environ.get('MYSQL_CONTAINER')
def command(binary):
    if container:return ['docker','exec','-i','-e','MYSQL_PWD',container,binary,'-uroot']
    cmd=[os.environ.get('MYSQL_BIN_DIR','')+binary,'-u'+os.environ.get('MYSQL_USER','root')]
    if os.environ.get('MYSQL_SOCKET'):cmd+=['--socket='+os.environ['MYSQL_SOCKET']]
    else:cmd+=['-h'+os.environ.get('MYSQL_HOST','127.0.0.1'),'-P'+os.environ.get('MYSQL_PORT','3306')]
    return cmd
def sql(query,database=None):
    cmd=command('mysql')+['--batch','--skip-column-names']+([database] if database else [])
    return subprocess.run(cmd,input=query,text=True,check=True,capture_output=True).stdout.strip()
def file(name,database=None):return sql((ROOT/name).read_text(),database)
if args.phase=='upgrade':
    file('src/test/resources/mysql/schema-v1.sql')
    sql("""INSERT INTO users(id,username,password_hash,real_name,user_type,active) VALUES(920001,'migration_teacher','unused-hash','Fixture','TEACHER',true);
    INSERT INTO questions(id,content,content_hash,options,correct_option,difficulty) VALUES(920001,'Legacy original','migration-fixture','{\"A\":\"a\",\"B\":\"b\"}','A',1);
    INSERT INTO teacher_events(id,teacher_id,teacher_name,event_title,event_description,event_timestamp,is_global) VALUES(920001,920001,'Fixture','Legacy task','QIDS:920001,920002',CURRENT_TIMESTAMP,true);
    INSERT INTO answer_records(id,user_id,question_id,session_id,selected_option,correct,answered_at) VALUES('migration-attempt',920001,920001,'legacy','B',false,CURRENT_TIMESTAMP);""",'zhixuewuli')
    file('src/main/resources/sql/upgrades/V2__archive_teaching_events.sql','zhixuewuli')
    file('src/main/resources/sql/upgrades/V3__task_snapshots_and_attempts.sql','zhixuewuli')
    assert sql('SELECT GROUP_CONCAT(snapshot_origin ORDER BY position_number) FROM teacher_event_questions','zhixuewuli')=='MIGRATED,MISSING'
    assert sql("SELECT CONCAT(practice,':',attempt_order) FROM answer_records WHERE id='migration-attempt'",'zhixuewuli')=='1:1'
    assert sql('SELECT token_version FROM users WHERE id=920001','zhixuewuli')=='1'
    # Leave an upgraded empty schema for the same service contract tests as the fresh schema.
    sql('DELETE FROM answer_records;DELETE FROM teacher_event_questions;DELETE FROM teacher_events;DELETE FROM questions;DELETE FROM users;','zhixuewuli')
    print(json.dumps({'upgrade':'passed','origins':['MIGRATED','MISSING'],'legacyAttemptPreserved':True,'oldTokensInvalidated':True}))
else:
    tables=sql('SHOW TABLES','zhixuewuli').splitlines()
    counts={table:int(sql('SELECT COUNT(*) FROM `'+table+'`','zhixuewuli')) for table in tables}
    with tempfile.TemporaryDirectory(prefix='physics-backup-') as directory:
        backup=pathlib.Path(directory)/'backup.sql'
        with backup.open('w') as output:
            subprocess.run(command('mysqldump')+['--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--column-statistics=0','zhixuewuli'],stdout=output,check=True)
        sql('DROP DATABASE IF EXISTS physics_restore_check; CREATE DATABASE physics_restore_check CHARACTER SET utf8mb4;')
        sql(backup.read_text(),'physics_restore_check')
        restored={table:int(sql('SELECT COUNT(*) FROM `'+table+'`','physics_restore_check')) for table in tables}
        assert counts==restored,(counts,restored)
        # CHECKSUM validates row content as well as counts, including snapshot/evidence columns.
        for table in tables:
            before=sql('CHECKSUM TABLE `'+table+'`','zhixuewuli').split('\t')[-1]
            after=sql('CHECKSUM TABLE `'+table+'`','physics_restore_check').split('\t')[-1]
            assert before==after,(table,before,after)
        sql('DROP DATABASE physics_restore_check')
        print(json.dumps({'backupRestore':'passed','tableCount':len(tables),'rowCounts':counts,'contentChecksumsEqual':True}))
