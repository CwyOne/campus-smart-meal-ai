package com.sky.store;

import com.sky.bean.ChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

// 实现ChatMemoryStore接口，用于将AI助手的聊天记录持久化到MongoDB
// 这样AI就能记住用户的多轮对话上下文，实现连续对话功能
@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 根据记忆ID获取聊天记录
     * @param memoryId 会话记忆ID，用于标识不同用户的对话
     * @return 聊天记录列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 构建查询条件：查找memoryId字段等于指定值的记录
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);

        // 从MongoDB中查询一条匹配的聊天记录
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);

        // 如果没有找到记录，返回空列表（表示这是新会话）
        if (chatMessages == null) {
            return new LinkedList<>();
        }
        // 将JSON格式的聊天内容反序列化为ChatMessage对象列表
        String contentJson = chatMessages.getContent();
        return ChatMessageDeserializer.messagesFromJson(contentJson);
    }

    /**
     * 更新聊天记录（保存或更新对话内容）
     * @param memoryId 会话记忆ID
     * @param list 完整的聊天记录列表（包含新旧消息）
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        // 构建查询条件：根据memoryId定位对应的会话记录
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);

        // 构建更新操作
        Update update = new Update();
        // 将聊天记录列表序列化为JSON字符串并保存到content字段
        update.set("content", ChatMessageSerializer.messagesToJson(list));
        
        // 执行upsert操作：如果记录存在则更新，不存在则插入新记录
        mongoTemplate.upsert(query, update, ChatMessages.class);
    }

    /**
     * 删除指定会话的所有聊天记录
     * @param memoryId 会话记忆ID
     */
    @Override
    public void deleteMessages(Object memoryId) {
        // 构建查询条件：根据memoryId定位对应的会话记录
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);

        // 从MongoDB中删除该会话的聊天记录
        mongoTemplate.remove(query, ChatMessages.class);
    }
}
