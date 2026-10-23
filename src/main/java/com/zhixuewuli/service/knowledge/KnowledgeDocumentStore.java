package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class KnowledgeDocumentStore {
    public record StoredFile(String key,String mediaType,String sha256,long bytes) {}
    private final KnowledgeProperties properties;
    public StoredFile store(MultipartFile file) {
        long limit=Math.min(properties.getMaxFileBytes(),10L*1024*1024);
        if(file==null || file.isEmpty() || limit<1 || file.getSize()>limit)throw new BusinessException("资料为空或超过大小上限");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        boolean pdf=filename.endsWith(".pdf");
        if(!pdf && !filename.endsWith(".md") && !filename.endsWith(".txt"))throw new BusinessException("只支持Markdown、TXT或文本PDF");
        final byte[] bytes;
        try(var input=file.getInputStream()){bytes=input.readNBytes((int)limit+1);}
        catch(IOException error){throw new IllegalStateException("无法读取上传资料",error);}
        if(bytes.length==0 || bytes.length>limit)throw new BusinessException("资料为空或超过大小上限");
        if(pdf) {
            if(bytes.length<5 || !new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new BusinessException("PDF文件格式无效");
        } else {
            try {
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if(text.isBlank() || text.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
                    throw new BusinessException("文本必须为有效UTF-8内容");
            } catch(CharacterCodingException error){throw new BusinessException("文本必须为有效UTF-8内容");}
        }
        String key=UUID.randomUUID()+".bin";
        try {
            Path root=Path.of(properties.getStoragePath()).toAbsolutePath().normalize();Files.createDirectories(root);
            Files.write(path(key),bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        } catch(IOException error){throw new IllegalStateException("无法保存资料",error);}
        return new StoredFile(key,pdf?"application/pdf":"text/plain",DigestUtils.sha256Hex(bytes),bytes.length);
    }
    public Path path(String key) {
        if(key==null || !key.matches("[0-9a-f-]{36}\\.bin"))throw new IllegalArgumentException("无效资料存储标识");
        Path result=Path.of(properties.getStoragePath()).toAbsolutePath().normalize().resolve(key);
        if(Files.isSymbolicLink(result))throw new IllegalArgumentException("无效资料存储标识");
        return result;
    }
    public void delete(String key) {
        try {Files.deleteIfExists(path(key));}
        catch(IOException error){throw new IllegalStateException("无法清理资料",error);}
    }
}
