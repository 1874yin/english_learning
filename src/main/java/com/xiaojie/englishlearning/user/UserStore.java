package com.xiaojie.englishlearning.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserStore {
    private final Path file;
    private final ObjectMapper mapper;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String dummyHash = passwords.encode("unused-dummy-password");
    private List<Account> accounts = new ArrayList<>();

    public UserStore(StoragePaths paths, ObjectMapper mapper) {
        this.file = paths.getRoot().resolve("users.json");
        this.mapper = mapper;
    }

    @PostConstruct
    public synchronized void load() throws IOException {
        if (Files.exists(file)) {
            accounts = mapper.readValue(file.toFile(), new TypeReference<List<Account>>() { });
        }
    }

    public Account register(String rawUsername, String password) throws IOException {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!username.matches("[\\p{L}\\p{N}_.-]{3,30}")) {
            throw new UserException(HttpStatus.BAD_REQUEST, "用户名须为 3–30 位文字、数字、下划线、短横线或点。" );
        }
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new UserException(HttpStatus.BAD_REQUEST, "密码至少 8 位，且 UTF-8 编码不超过 72 字节。" );
        }
        String hash = passwords.encode(password);
        synchronized (this) {
            if (findByName(username) != null) {
                throw new UserException(HttpStatus.CONFLICT, "用户名已被注册，请换一个。" );
            }
            Account account = new Account();
            account.id = UUID.randomUUID().toString();
            account.username = username;
            account.passwordHash = hash;
            account.createdAt = Instant.now().toString();
            List<Account> next = new ArrayList<>(accounts);
            next.add(account);
            Path temporary = file.resolveSibling("users.json.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), next);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            accounts = next;
            return account;
        }
    }

    public Account authenticate(String username, String password) {
        Account account;
        synchronized (this) { account = findByName(username == null ? "" : username.trim()); }
        boolean valid = password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(password, account == null ? dummyHash : account.passwordHash);
        if (account == null || !valid) {
            throw new UserException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确。" );
        }
        return account;
    }

    public synchronized Account findById(String id) {
        for (Account account : accounts) {
            if (account.id.equals(id)) return account;
        }
        return null;
    }

    public Map<String, String> view(Account account) {
        if (account == null) return null;
        Map<String, String> view = new LinkedHashMap<>();
        view.put("id", account.id);
        view.put("username", account.username);
        view.put("createdAt", account.createdAt);
        return view;
    }

    private Account findByName(String name) {
        for (Account account : accounts) {
            if (account.username.equalsIgnoreCase(name)) return account;
        }
        return null;
    }

    public static class Account {
        public String id;
        public String username;
        public String passwordHash;
        public String createdAt;
    }
}
