package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.dto.knowledge.AiMessageSourceView;
import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiRunHistoryService {
    private final AiRunRepository runs; private final AiMessageSourceRepository sources;
    /**
     * 班级资料库已下线：历史来源统一标记为不可用，不再回源文档内容。
     * 保留此处理是为了不把已撤回资料的原文继续展示给用户。
     */
    public ConversationHistory attachSources(ConversationHistory history,Long viewerId) {
        var run=runs.findByConversationHistoryId(history.getId());if(run.isEmpty())return history;
        var sourceEntities=sources.findByRunIdOrderById(run.get().getId()); var views=new ArrayList<AiMessageSourceView>(); boolean inaccessible=false;
        for(var source:sourceEntities) {
            inaccessible=true;
            views.add(new AiMessageSourceView(source.getCitationId(),source.getDocumentId(),source.getRevisionId(),"资料已不可用",null,null,null,false));
        }
        history.setAiRunId(run.get().getId());history.setSources(List.copyOf(views));
        if(inaccessible)history.setAnswer("本次回答引用的部分资料已撤回或当前无权访问，无法继续查看该回答的证据内容。");
        return history;
    }
    public void refreshContext(SessionContextInternalDTO context,Long viewerId) {
        var lock=context.getLock();lock.writeLock().lock();
        try {var records=context.getInteractionRecords();if(records==null)return;
            for(var entry:new ArrayList<>(records.entrySet())){var fresh=attachSources(entry.getValue(),viewerId);if(fresh!=null)records.put(entry.getKey(),fresh);}
        } finally {lock.writeLock().unlock();}
    }
}
