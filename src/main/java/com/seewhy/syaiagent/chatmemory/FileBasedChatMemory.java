package com.seewhy.syaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于文件持久化的对话记忆
 */
public class FileBasedChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(FileBasedChatMemory.class);
    private static final Pattern UNSAFE_CONVERSATION_ID_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final int MAX_CONVERSATION_ID_LENGTH = 80;

    private final String BASE_DIR;
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        // 设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                synchronized (kryo) {
                    messages = kryo.readObject(input, ArrayList.class);
                }
            } catch (IOException e) {
                log.warn("Failed to read chat memory file {}: {}", file.getName(), e.getMessage());
            }
        }
        return messages;
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            synchronized (kryo) {
                kryo.writeObject(output, messages);
            }
        } catch (IOException e) {
            log.warn("Failed to save chat memory file {}: {}", file.getName(), e.getMessage());
        }
    }

    private File getConversationFile(String conversationId) {
        File baseDir = new File(BASE_DIR).getAbsoluteFile();
        File file = new File(baseDir, safeConversationId(conversationId) + ".kryo").getAbsoluteFile();
        if (!file.toPath().normalize().startsWith(baseDir.toPath().normalize())) {
            throw new SecurityException("Conversation memory path is outside the allowed directory.");
        }
        return file;
    }

    private String safeConversationId(String conversationId) {
        String normalized = conversationId == null || conversationId.isBlank() ? "default" : conversationId.strip();
        String safe = UNSAFE_CONVERSATION_ID_CHARS.matcher(normalized).replaceAll("_");
        if (safe.length() > MAX_CONVERSATION_ID_LENGTH) {
            safe = safe.substring(0, MAX_CONVERSATION_ID_LENGTH);
        }
        return safe.isBlank() ? "default" : safe;
    }
}
