package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Service
public class FSStoreService implements StoreService {
    @Override
    public void store(String what, String path) {
        File file = new File(path);
        if (file.exists()) {
            String fileName = file.getName();
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex);
                fileName = fileName.substring(0, dotIndex);
            }
            String parentPath = file.getParent();
            file = new File(parentPath, fileName + System.currentTimeMillis() + extension);
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(what);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
