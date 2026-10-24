#!/usr/bin/env python3
"""向本地环境灌入演示用测试数据：题库、班级、学生、任务与作答记录。

用法（后端需已在 8080 运行）：
    python3 scripts/seed_test_data.py
"""

from __future__ import annotations

import json
import random
import sys
import urllib.error
import urllib.request
from pathlib import Path
import uuid
from datetime import datetime, timedelta, timezone
from urllib.parse import urlencode

BASE = "http://127.0.0.1:8080/api/v1"
TEACHER = ("teacher", "Teacher@2026")
rng = random.Random(20261020)


def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def fmt(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


def req(method: str, path: str, token: str | None = None, body=None, session_id: str | None = None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", f"Bearer {token}")
    if session_id:
        r.add_header("X-Session-Id", session_id)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def data_of(body: dict):
    return body.get("data")


def build_question_search_path(query: str) -> str:
    return "/questions/teacher/page?" + urlencode({"page": 1, "size": 20, "search": query})


def find_exact_question(rows: list[dict], content: str) -> dict | None:
    matches = [row for row in rows if row.get("content", "").strip() == content.strip()]
    return min(matches, key=lambda row: row["id"]) if matches else None


def login(username: str, password: str):
    status, body = req("POST", "/auth/login", body={"username": username, "password": password})
    if status != 200 or "data" not in body:
        raise RuntimeError(f"登录失败 {username}: {status} {body}")
    return body["data"]["token"], body["data"]


# ---------------------------------------------------------------- 题库
QUESTIONS = [
    {
        "content": "下列物理量中，属于矢量的是（　　）",
        "options": {"A": "路程", "B": "时间", "C": "速度", "D": "质量"},
        "correct_option": "C",
        "explanation": "矢量既有大小又有方向。路程、时间、质量只有大小没有方向，是标量；速度既有大小又有方向，是矢量。",
        "difficulty": 1,
    },
    {
        "content": "关于参考系，下列说法正确的是（　　）",
        "options": {
            "A": "参考系必须选择静止不动的物体",
            "B": "研究物体运动时，选择不同的参考系，观察结果可能不同",
            "C": "参考系一旦选定，就不能改变",
            "D": "地面一定是最好的参考系",
        },
        "correct_option": "B",
        "explanation": "参考系可以任意选取，运动和静止都是相对参考系而言的。选择不同参考系，对同一运动的描述可能不同。",
        "difficulty": 1,
    },
    {
        "content": "一小球沿直线运动，前 $2\\,\\mathrm{s}$ 内的位移为 $4\\,\\mathrm{m}$，后 $2\\,\\mathrm{s}$ 内的位移为 $6\\,\\mathrm{m}$，则全程的平均速度大小为（　　）",
        "options": {
            "A": "$2\\,\\mathrm{m/s}$",
            "B": "$2.5\\,\\mathrm{m/s}$",
            "C": "$3\\,\\mathrm{m/s}$",
            "D": "$5\\,\\mathrm{m/s}$",
        },
        "correct_option": "B",
        "explanation": "全程位移 $x=4+6=10\\,\\mathrm{m}$，总时间 $t=4\\,\\mathrm{s}$，平均速度 $\\bar{v}=x/t=2.5\\,\\mathrm{m/s}$。",
        "difficulty": 2,
    },
    {
        "content": "物体做匀加速直线运动，初速度 $v_0=2\\,\\mathrm{m/s}$，加速度 $a=1\\,\\mathrm{m/s^2}$，则 $3\\,\\mathrm{s}$ 末的速度为（　　）",
        "options": {
            "A": "$3\\,\\mathrm{m/s}$",
            "B": "$4\\,\\mathrm{m/s}$",
            "C": "$5\\,\\mathrm{m/s}$",
            "D": "$6\\,\\mathrm{m/s}$",
        },
        "correct_option": "C",
        "explanation": "由 $v=v_0+at=2+1\\times 3=5\\,\\mathrm{m/s}$。",
        "difficulty": 2,
    },
    {
        "content": "自由落体运动中，物体在第 $1\\,\\mathrm{s}$ 内下落的高度约为（$g$ 取 $10\\,\\mathrm{m/s^2}$）（　　）",
        "options": {
            "A": "$2\\,\\mathrm{m}$",
            "B": "$5\\,\\mathrm{m}$",
            "C": "$10\\,\\mathrm{m}$",
            "D": "$20\\,\\mathrm{m}$",
        },
        "correct_option": "B",
        "explanation": "自由落体 $h=\\frac{1}{2}gt^2=\\frac{1}{2}\\times 10\\times 1^2=5\\,\\mathrm{m}$。",
        "difficulty": 2,
    },
    {
        "content": "关于惯性，下列说法正确的是（　　）",
        "options": {
            "A": "物体速度越大，惯性越大",
            "B": "物体受力越大，惯性越小",
            "C": "质量越大的物体，惯性越大",
            "D": "静止的物体没有惯性",
        },
        "correct_option": "C",
        "explanation": "惯性是物体保持原有运动状态的性质，只与质量有关，与速度、受力无关。任何物体在任何状态下都有惯性。",
        "difficulty": 1,
    },
    {
        "content": "质量 $m=2\\,\\mathrm{kg}$ 的物体，受到 $F=6\\,\\mathrm{N}$ 的合力，其加速度为（　　）",
        "options": {
            "A": "$1\\,\\mathrm{m/s^2}$",
            "B": "$2\\,\\mathrm{m/s^2}$",
            "C": "$3\\,\\mathrm{m/s^2}$",
            "D": "$12\\,\\mathrm{m/s^2}$",
        },
        "correct_option": "C",
        "explanation": "由牛顿第二定律 $F=ma$，得 $a=F/m=6/2=3\\,\\mathrm{m/s^2}$。",
        "difficulty": 2,
    },
    {
        "content": "关于牛顿第三定律，下列说法正确的是（　　）",
        "options": {
            "A": "作用力与反作用力可以相互抵消",
            "B": "作用力与反作用力作用在同一物体上",
            "C": "作用力与反作用力大小相等、方向相反，作用在同一直线上",
            "D": "只有物体静止时才有反作用力",
        },
        "correct_option": "C",
        "explanation": "作用力与反作用力总是成对出现，大小相等方向相反，作用在两个不同物体上，不能抵消，与运动状态无关。",
        "difficulty": 2,
    },
    {
        "content": "用 $50\\,\\mathrm{N}$ 的力沿水平方向推动质量 $10\\,\\mathrm{kg}$ 的箱子，地面光滑，箱子的加速度为（　　）",
        "options": {
            "A": "$0.2\\,\\mathrm{m/s^2}$",
            "B": "$2\\,\\mathrm{m/s^2}$",
            "C": "$5\\,\\mathrm{m/s^2}$",
            "D": "$500\\,\\mathrm{m/s^2}$",
        },
        "correct_option": "C",
        "explanation": "光滑水平面上合力即推力，$a=F/m=50/10=5\\,\\mathrm{m/s^2}$。",
        "difficulty": 2,
    },
    {
        "content": "关于摩擦力，下列说法正确的是（　　）",
        "options": {
            "A": "摩擦力的方向总是与物体运动方向相反",
            "B": "静止的物体不可能受到滑动摩擦力",
            "C": "滑动摩擦力的大小与正压力成正比",
            "D": "接触面越粗糙，静摩擦力一定越大",
        },
        "correct_option": "C",
        "explanation": "滑动摩擦力 $f=\\mu N$，与正压力成正比。摩擦力方向与相对运动（趋势）方向相反，不一定与运动方向相反。",
        "difficulty": 3,
    },
    {
        "content": "重 $100\\,\\mathrm{N}$ 的物体放在水平地面上，用 $30\\,\\mathrm{N}$ 的水平力未推动，此时摩擦力大小为（　　）",
        "options": {
            "A": "$0\\,\\mathrm{N}$",
            "B": "$30\\,\\mathrm{N}$",
            "C": "$70\\,\\mathrm{N}$",
            "D": "$100\\,\\mathrm{N}$",
        },
        "correct_option": "B",
        "explanation": "物体静止，合力为零。水平方向上静摩擦力与推力平衡，故 $f=30\\,\\mathrm{N}$。",
        "difficulty": 3,
    },
    {
        "content": "关于功和功率，下列说法正确的是（　　）",
        "options": {
            "A": "力越大，做功一定越多",
            "B": "功率越大，做功一定越多",
            "C": "做功的两个必要因素是力和力方向上的位移",
            "D": "功是矢量",
        },
        "correct_option": "C",
        "explanation": "功 $W=Fs\\cos\\theta$，取决于力、位移及二者夹角。功是标量。功率表示做功快慢，$P=W/t$。",
        "difficulty": 2,
    },
    {
        "content": "质量 $m=1\\,\\mathrm{kg}$ 的物体被匀速提升 $2\\,\\mathrm{m}$，克服重力做功约为（$g$ 取 $10\\,\\mathrm{m/s^2}$）（　　）",
        "options": {
            "A": "$2\\,\\mathrm{J}$",
            "B": "$5\\,\\mathrm{J}$",
            "C": "$10\\,\\mathrm{J}$",
            "D": "$20\\,\\mathrm{J}$",
        },
        "correct_option": "D",
        "explanation": "克服重力做功 $W=mgh=1\\times 10\\times 2=20\\,\\mathrm{J}$。",
        "difficulty": 2,
    },
    {
        "content": "关于动能，下列说法正确的是（　　）",
        "options": {
            "A": "速度越大，动能一定越大",
            "B": "动能 $E_k=\\frac{1}{2}mv^2$，是标量",
            "C": "动能可以为负值",
            "D": "质量越大动能越小",
        },
        "correct_option": "B",
        "explanation": "动能 $E_k=\\frac{1}{2}mv^2\\ge 0$，是标量，与质量成正比、与速度平方成正比。只说速度大而不提质量不能比较动能。",
        "difficulty": 2,
    },
    {
        "content": "机械能守恒的条件是（　　）",
        "options": {
            "A": "物体只受重力作用",
            "B": "物体只受重力或弹力作用，且只有重力或弹力做功",
            "C": "物体做匀速直线运动",
            "D": "合外力为零",
        },
        "correct_option": "B",
        "explanation": "机械能守恒的条件是：只有重力（或弹簧弹力）做功，其他力不做功或做功代数和为零。",
        "difficulty": 3,
    },
    {
        "content": "下列现象中，属于光的折射的是（　　）",
        "options": {
            "A": "平面镜成像",
            "B": "水中筷子看起来变弯",
            "C": "影子的形成",
            "D": "小孔成像",
        },
        "correct_option": "B",
        "explanation": "水中筷子变弯是光从水中斜射入空气时发生折射造成的。平面镜成像是反射，影子和小孔成像是光的直线传播。",
        "difficulty": 1,
    },
    {
        "content": "一束光从空气射入水中，入射角为 $45^\\circ$，则折射角（　　）",
        "options": {
            "A": "大于 $45^\\circ$",
            "B": "等于 $45^\\circ$",
            "C": "小于 $45^\\circ$",
            "D": "无法判断",
        },
        "correct_option": "C",
        "explanation": "光从光疏介质斜射入光密介质时，折射角小于入射角。",
        "difficulty": 2,
    },
    {
        "content": "凸透镜成像时，当物距 $u>2f$，所成的像是（　　）",
        "options": {
            "A": "正立放大的虚像",
            "B": "倒立放大的实像",
            "C": "倒立缩小的实像",
            "D": "正立缩小的虚像",
        },
        "correct_option": "C",
        "explanation": "当 $u>2f$ 时，像距 $f<v<2f$，成倒立、缩小的实像，应用如照相机。",
        "difficulty": 3,
    },
    {
        "content": "关于热量和温度，下列说法正确的是（　　）",
        "options": {
            "A": "温度高的物体含有的热量多",
            "B": "热量可以从低温物体传向高温物体（伴随其他变化）",
            "C": "物体温度升高，一定吸收了热量",
            "D": "0℃ 的冰没有内能",
        },
        "correct_option": "B",
        "explanation": "热量是过程量，不能说“含有”。温度升高也可能是做功引起的。任何物体在任何温度下都有内能。热量可自发地从高温传向低温；从低温传向高温需伴随其他变化（如电冰箱）。",
        "difficulty": 3,
    },
    {
        "content": "质量 $m=2\\,\\mathrm{kg}$ 的水温度升高 $10^\\circ\\mathrm{C}$，吸收的热量约为（$c=4.2\\times 10^3\\,\\mathrm{J/(kg\\cdot ^\\circ C)}$）（　　）",
        "options": {
            "A": "$2.1\\times 10^3\\,\\mathrm{J}$",
            "B": "$4.2\\times 10^3\\,\\mathrm{J}$",
            "C": "$8.4\\times 10^4\\,\\mathrm{J}$",
            "D": "$8.4\\times 10^5\\,\\mathrm{J}$",
        },
        "correct_option": "C",
        "explanation": "由 $Q=cm\\Delta t=4.2\\times 10^3\\times 2\\times 10=8.4\\times 10^4\\,\\mathrm{J}$。",
        "difficulty": 2,
    },
    {
        "content": "导体中的电流为 $0.5\\,\\mathrm{A}$，则 $10\\,\\mathrm{s}$ 内通过导体横截面的电荷量为（　　）",
        "options": {
            "A": "$2\\,\\mathrm{C}$",
            "B": "$5\\,\\mathrm{C}$",
            "C": "$10\\,\\mathrm{C}$",
            "D": "$20\\,\\mathrm{C}$",
        },
        "correct_option": "B",
        "explanation": "由 $I=q/t$ 得 $q=It=0.5\\times 10=5\\,\\mathrm{C}$。",
        "difficulty": 2,
    },
    {
        "content": "一段导体两端电压为 $6\\,\\mathrm{V}$，通过的电流为 $0.3\\,\\mathrm{A}$，则其电阻为（　　）",
        "options": {
            "A": "$2\\,\\Omega$",
            "B": "$18\\,\\Omega$",
            "C": "$20\\,\\Omega$",
            "D": "$1.8\\,\\Omega$",
        },
        "correct_option": "C",
        "explanation": "由欧姆定律 $R=U/I=6/0.3=20\\,\\Omega$。",
        "difficulty": 2,
    },
    {
        "content": "关于串联电路，下列说法正确的是（　　）",
        "options": {
            "A": "各处电流可能不相等",
            "B": "总电压等于各部分电压之和",
            "C": "总电阻小于任一分电阻",
            "D": "一条支路断开，其他支路仍工作",
        },
        "correct_option": "B",
        "explanation": "串联电路电流处处相等，总电压等于各部分电压之和，总电阻等于各电阻之和（大于任一分电阻）。一处断开则整个电路断路。",
        "difficulty": 2,
    },
    {
        "content": "两个阻值均为 $6\\,\\Omega$ 的电阻并联，总电阻为（　　）",
        "options": {
            "A": "$3\\,\\Omega$",
            "B": "$6\\,\\Omega$",
            "C": "$12\\,\\Omega$",
            "D": "$36\\,\\Omega$",
        },
        "correct_option": "A",
        "explanation": "两个相同电阻并联，$R_{总}=R/2=3\\,\\Omega$。或由 $1/R=1/6+1/6=1/3$。",
        "difficulty": 2,
    },
    {
        "content": "电功率 $P=100\\,\\mathrm{W}$ 的用电器工作 $5\\,\\mathrm{min}$，消耗的电能为（　　）",
        "options": {
            "A": "$500\\,\\mathrm{J}$",
            "B": "$3000\\,\\mathrm{J}$",
            "C": "$3\\times 10^4\\,\\mathrm{J}$",
            "D": "$3\\times 10^5\\,\\mathrm{J}$",
        },
        "correct_option": "C",
        "explanation": "$W=Pt=100\\times 300=3\\times 10^4\\,\\mathrm{J}$。",
        "difficulty": 2,
    },
    {
        "content": "下列做法中，符合安全用电原则的是（　　）",
        "options": {
            "A": "用湿手拔插头",
            "B": "电线上晾衣服",
            "C": "金属外壳用电器接地",
            "D": "多个大功率用电器共用一个插座",
        },
        "correct_option": "C",
        "explanation": "金属外壳用电器接地可防止漏电时外壳带电造成触电。湿手操作、电线上晾物、多大功率共用插座都易引发触电或火灾。",
        "difficulty": 1,
    },
]

BASE_STUDENTS = [
    ("stu01", "Student@2026", "张伟", "高一(1)班"),
    ("stu02", "Student@2026", "王芳", "高一(1)班"),
    ("stu03", "Student@2026", "李娜", "高一(1)班"),
    ("stu04", "Student@2026", "刘洋", "高一(1)班"),
    ("stu05", "Student@2026", "陈静", "高一(1)班"),
    ("stu06", "Student@2026", "杨帆", "高一(1)班"),
    ("stu07", "Student@2026", "赵磊", "高一(1)班"),
    ("stu08", "Student@2026", "黄敏", "高一(2)班"),
    ("stu09", "Student@2026", "周杰", "高一(2)班"),
    ("stu10", "Student@2026", "吴倩", "高一(2)班"),
    ("stu11", "Student@2026", "徐强", "高一(2)班"),
    ("stu12", "Student@2026", "孙丽", "高一(2)班"),
]


def build_students() -> list[tuple[str, str, str, str]]:
    """Create a stable 60-student showcase cohort, balanced across two classes."""
    surnames = ["何", "罗", "高", "马", "林", "郭", "郑", "梁"]
    given_names = ["晨", "悦", "宇", "欣", "浩", "宁", "涵", "杰", "妍", "博", "琪", "轩"]
    generated = []
    for index in range(48):
        name = surnames[(index // len(given_names)) % len(surnames)] + given_names[index % len(given_names)]
        class_name = "高一(1)班" if index < 23 else "高一(2)班"
        generated.append((f"demo_g1_{index + 1:03d}", "Student@2026", name, class_name))
    return [*BASE_STUDENTS, *generated]


STUDENTS = build_students()

QUESTION_POINT_CODES = {
    QUESTIONS[0]["content"]: ["mechanics.units"],
    QUESTIONS[3]["content"]: ["mechanics.acceleration"],
    QUESTIONS[4]["content"]: ["mechanics.acceleration"],
    QUESTIONS[5]["content"]: ["mechanics.inertia"],
    QUESTIONS[6]["content"]: ["mechanics.newton-laws", "mechanics.resultant-force"],
    QUESTIONS[7]["content"]: ["mechanics.action-reaction"],
    QUESTIONS[8]["content"]: ["mechanics.newton-laws", "mechanics.acceleration"],
    QUESTIONS[9]["content"]: ["mechanics.friction"],
    QUESTIONS[10]["content"]: ["mechanics.friction", "mechanics.resultant-force"],
}


def knowledge_codes_for_question(question: dict) -> list[str]:
    return QUESTION_POINT_CODES.get(question["content"], [])


def create_classes(token: str) -> dict[str, int]:
    ids: dict[str, int] = {}
    _, body = req("GET", "/teacher/classes", token)
    existing = {c["name"]: c["id"] for c in (data_of(body) or [])}
    for name, grade in [("高一(1)班", "高一"), ("高一(2)班", "高一")]:
        if name in existing:
            ids[name] = existing[name]
            continue
        st, body = req("POST", "/teacher/classes", token, {"name": name, "grade": grade})
        if st in (200, 201) and data_of(body):
            ids[name] = body["data"]["id"]
        else:
            raise RuntimeError(f"创建班级失败 {name}: {st} {body}")
    # 教师挂到默认班级，才能把注册学生挪出来
    req("POST", "/teacher/classes/1/teachers", token, {"userId": 1})
    return ids


def register_students() -> list[dict]:
    created: list[dict] = []
    for username, password, real_name, class_name in STUDENTS:
        st, body = req(
            "POST",
            "/auth/register",
            body={
                "username": username,
                "password": password,
                "realName": real_name,
                "userType": "STUDENT",
                "className": class_name,
            },
        )
        if st not in (200, 201):
            msg = str(body)
            if not any(k in msg for k in ("存在", "占用", "唯一", "已注册")):
                print(f"  ! 注册 {username}: {st} {body}")
        _, data = login(username, password)
        created.append(
            {"id": data["userId"], "username": username, "password": password,
             "realName": real_name, "className": class_name}
        )
    return created


def move_students(token: str, students: list[dict], class_ids: dict[str, int]) -> None:
    for s in students:
        target = class_ids[s["className"]]
        for cid in {1, *class_ids.values()}:
            req("DELETE", f"/teacher/classes/{cid}/students/{s['id']}", token)
        st, body = req("POST", f"/teacher/classes/{target}/students", token, {"userId": s["id"]})
        if st not in (200, 201):
            print(f"  ! 入班失败 {s['username']} -> {s['className']}: {st} {body}")


def create_questions(token: str) -> list[tuple[int, str]]:
    """返回 [(questionId, correctOption)]，按创建成功顺序。"""
    result: list[tuple[int, str]] = []
    for q in QUESTIONS:
        payload = {
            "content": q["content"],
            "options": json.dumps(q["options"], ensure_ascii=False),
            "correctOption": q["correct_option"],
            "explanation": q["explanation"],
            "difficulty": q["difficulty"],
            "knowledgePointCodes": knowledge_codes_for_question(q),
        }
        st, body = req("POST", "/questions/teacher/create", token, payload)
        if st in (200, 201) and data_of(body):
            result.append((body["data"]["id"], body["data"]["correctOption"]))
            continue
        # 已存在则按内容搜索定位
        st2, body2 = req("GET", build_question_search_path(q["content"][:8]), token)
        rows = (data_of(body2) or {}).get("records") or (data_of(body2) or {}).get("list") or []
        exact = find_exact_question(rows, q["content"])
        if exact:
            result.append((exact["id"], q["correct_option"]))
        else:
            print(f"  ! 题目未精确匹配: {st} {body}; 查询={st2} {body2}")
    return result


def sync_question_knowledge_tags(token: str, bank: list[tuple[int, str]]) -> None:
    for question_id, _ in bank:
        status, body = req("GET", f"/questions/teacher/get/{question_id}", token)
        question = data_of(body) if status == 200 else None
        if not question:
            raise RuntimeError(f"读取题目失败 {question_id}: {status} {body}")
        codes = knowledge_codes_for_question(question)
        if question.get("knowledgePointCodes") == codes:
            continue
        payload = {
            "id": question_id,
            "content": question["content"],
            "options": json.dumps(question["options"], ensure_ascii=False),
            "correctOption": question["correctOption"],
            "explanation": question.get("explanation") or "",
            "difficulty": question.get("difficulty"),
            "knowledgePointCodes": codes,
            "imageBase64": question.get("imageBase64"),
        }
        status, body = req("PUT", "/questions/teacher/update", token, payload)
        if status not in (200, 201):
            raise RuntimeError(f"标注知识点失败 {question_id}: {status} {body}")


def publish_task(token, title, desc, qids_with_correct, class_id, start, due, status="PUBLISHED"):
    _, existing_body = req("GET", "/teacher/events/all", token)
    existing = data_of(existing_body) or []
    for event in existing:
        if event.get("eventTitle") == title:
            print(f"  复用已有任务 {title} (id={event['id']})")
            return event["id"]
    payload = {
        "questionIds": [qid for qid, _ in qids_with_correct],
        "eventTitle": title,
        "eventDescription": desc,
        "eventTimestamp": fmt(start),
        "status": status,
        "startAt": fmt(start),
        "dueAt": fmt(due),
        "classId": class_id,
    }
    st, body = req("POST", "/teacher/events", token, payload)
    if st in (200, 201) and data_of(body):
        return body["data"]["id"]
    print(f"  ! 发布失败 {title}: {st} {body}")
    return None


def get_task_question_map(token: str, event_id: int) -> dict[int, str]:
    status, body = req("GET", f"/teacher/events/{event_id}/questions", token)
    if status != 200:
        raise RuntimeError(f"读取任务题目失败 {event_id}: {status} {body}")
    return {row["id"]: row["correctOption"] for row in (data_of(body) or [])}


def upload_knowledge_fixtures(token: str, class_ids: dict[str, int]) -> None:
    fixtures = [
        ("高一(1)班", "力学复习提要（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一1班_力学复习提要.md", "mechanics.md"),
        ("高一(1)班", "匀变速直线运动易错辨析（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一1班_匀变速直线运动.md", "kinematics.md"),
        ("高一(1)班", "牛顿定律与受力分析专题（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一1班_牛顿定律与受力分析.md", "newton-laws.md"),
        ("高一(1)班", "功和能守恒复习单（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一1班_功和能.md", "work-energy.md"),
        ("高一(2)班", "电学复习提要（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一2班_电学复习提要.md", "electricity.md"),
        ("高一(2)班", "电路实验与测量排错（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一2班_电路实验.md", "circuit-lab.md"),
        ("高一(2)班", "电功率与家庭用电（模拟资料）", Path(__file__).parent / "fixtures/knowledge/高一2班_电功率与安全用电.md", "power-safety.md"),
    ]
    for class_name, title, path, filename in fixtures:
        class_id = class_ids[class_name]
        _, listing = req("GET", f"/knowledge/documents?classId={class_id}&page=1&size=50", token)
        records = (data_of(listing) or {}).get("records", [])
        if any(item.get("title") == title for item in records):
            print(f"  复用知识资料 {title}")
            continue
        boundary = "----CodexSeed" + uuid.uuid4().hex
        fields = [("classId", str(class_id)), ("title", title)]
        chunks = []
        for name, value in fields:
            chunks.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode())
        chunks.append(
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\nContent-Type: text/markdown; charset=utf-8\r\n\r\n".encode()
            + path.read_bytes() + b"\r\n"
        )
        chunks.append(f"--{boundary}--\r\n".encode())
        request = urllib.request.Request(
            BASE + "/knowledge/documents",
            data=b"".join(chunks),
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "Authorization": f"Bearer {token}"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = json.loads(response.read().decode("utf-8"))
                status = response.status
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"上传模拟知识资料失败 {title}: {error.code} {raw}") from error
        if status not in (200, 201, 202):
            raise RuntimeError(f"上传模拟知识资料失败 {title}: {status} {body}")
        print(f"  已提交知识资料 {title}，索引状态={data_of(body).get('status')}")


def submit_all(students, event_id, qmap: dict[int, str], accuracy: float, coverage: float) -> None:
    count = 0
    for s in students:
        if rng.random() > coverage:
            continue
        token, _ = login(s["username"], s["password"])
        session = f"seed-{s['username']}-e{event_id}"
        for qid, correct in qmap.items():
            if rng.random() > 0.93:
                continue
            ans = correct if rng.random() < accuracy else rng.choice([o for o in "ABCD" if o != correct])
            st, body = req("POST", "/answer-records/student/submit", token, {
                "eventId": event_id,
                "requestId": f"seed-{event_id}-{s['username']}-{qid}",
                "intent": "ASSIGNMENT",
                "questionId": qid,
                "userAnswer": ans,
            }, session_id=session)
            if st in (200, 201):
                count += 1
            else:
                msg = str(body)
                if any(k in msg for k in ("已完成", "截止", "requestId", "未开始", "尚未发布")):
                    continue
                print(f"  ! 作答 {s['username']} q{qid}: {st} {body}")
        # 补练 1~2 题
        for qid in rng.sample(list(qmap.keys()), k=min(2, len(qmap))):
            req("POST", "/answer-records/student/submit", token, {
                "eventId": event_id,
                "requestId": f"seed-p-{event_id}-{s['username']}-{qid}",
                "intent": "PRACTICE",
                "questionId": qid,
                "userAnswer": qmap[qid],
            }, session_id=session + "-p")
    print(f"  任务 {event_id}: 约 {count} 条首次作答 + 补练")


def main() -> int:
    print("== 教师登录 ==")
    token, info = login(*TEACHER)
    print(f"  {info['username']} id={info['userId']}")

    print("== 更新教师资料 ==")
    req("PUT", "/user/me", token, {"realName": "王老师"})

    print("== 班级 ==")
    class_ids = create_classes(token)
    print(f"  {class_ids}")

    print("== 班级模拟知识资料 ==")
    upload_knowledge_fixtures(token, class_ids)

    print("== 学生 ==")
    students = register_students()
    print(f"  {len(students)} 名")

    print("== 班级归属 ==")
    move_students(token, students, class_ids)

    print("== 题库 ==")
    bank = create_questions(token)
    sync_question_knowledge_tags(token, bank)
    print(f"  {len(bank)} 道")
    if len(bank) < 10:
        print("题目过少，终止")
        return 1

    # 分组
    def pick(start, end):
        return bank[max(0, start):min(len(bank), end)]

    t1_qs = pick(0, 8)       # 运动学/基础
    t2_qs = pick(5, 15)      # 牛顿/摩擦/功
    t3_qs = pick(20, 26)     # 电学
    t4_qs = pick(0, 12)      # 综合

    now = utc_now()
    class1, class2 = class_ids["高一(1)班"], class_ids["高一(2)班"]

    print("== 任务 ==")
    t1 = publish_task(token, "运动学基础练习", "直线运动、速度与加速度基础巩固",
                      t1_qs, class1, now - timedelta(days=7), now + timedelta(hours=1))
    t2 = publish_task(token, "牛顿运动定律", "牛顿三定律与摩擦力专项",
                      t2_qs, class1, now - timedelta(days=1), now + timedelta(days=7))
    t3 = publish_task(token, "电学入门测验", "电流、电压、电阻与电路连接",
                      t3_qs, class2, now + timedelta(days=3), now + timedelta(days=10))
    t4 = publish_task(token, "综合复习卷", "力热光电综合检测",
                      t4_qs, class2, now - timedelta(days=10), now + timedelta(hours=2))
    t5 = publish_task(token, "力学错因专项", "围绕受力分析与牛顿运动定律进行针对性巩固",
                      t2_qs[:8], class1, now - timedelta(hours=2), now + timedelta(days=3))
    t6 = publish_task(token, "光学单元练习（草稿）", "教师可继续编辑后发布的课堂练习",
                      t3_qs, class2, now + timedelta(days=1), now + timedelta(days=8), status="DRAFT")
    t7 = publish_task(token, "电学基础巩固", "围绕电流、电压与电阻开展课后巩固练习",
                      t3_qs, class2, now - timedelta(hours=1), now + timedelta(days=4))
    print(f"  t1={t1} t2={t2} t3={t3} t4={t4} t5={t5} t6={t6} t7={t7}")

    c1 = [s for s in students if s["className"] == "高一(1)班"]
    c2 = [s for s in students if s["className"] == "高一(2)班"]

    print("== 作答 ==")
    if t1:
        submit_all(c1, t1, get_task_question_map(token, t1), 0.72, 0.95)
    if t2:
        submit_all(c1, t2, get_task_question_map(token, t2), 0.65, 0.55)
    if t5:
        submit_all(c1, t5, get_task_question_map(token, t5), 0.52, 1.0)
    if t7:
        submit_all(c2, t7, get_task_question_map(token, t7), 0.68, 0.95)
    if t4:
        submit_all(c2, t4, get_task_question_map(token, t4), 0.70, 0.90)

    print("== 截止任务 ==")
    for eid in (t1, t4):
        if eid:
            _, event_body = req("GET", f"/teacher/events/{eid}", token)
            event = data_of(event_body) or {}
            if event.get("status") == "CLOSED":
                print(f"  任务 {eid} 已截止")
                continue
            st, body = req("POST", f"/teacher/events/{eid}/close", token)
            print(f"  close {eid}: {st}")

    print("\n完成。测试账号：")
    print("  教师  teacher / Teacher@2026")
    print("  学生  stu01 / Student@2026   （高一(1)班）")
    print("  学生  stu08 / Student@2026   （高一(2)班）")
    print("  学生  demo_g1_001..demo_g1_048 / Student@2026")
    return 0


if __name__ == "__main__":
    sys.exit(main())
