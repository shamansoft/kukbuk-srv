package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;

@Service
public class FSStoreService implements StoreService {
    @Override
    public void store(String what, String path) {
        java.io.File file = new java.io.File(path);
        if (file.exists()) {
            String fileName = file.getName();
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex);
                fileName = fileName.substring(0, dotIndex);
            }
            file = new java.io.File(fileName + System.currentTimeMillis() + extension);
        }
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            writer.write(what);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
