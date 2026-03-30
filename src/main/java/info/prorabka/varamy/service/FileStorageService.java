package info.prorabka.varamy.service;

import info.prorabka.varamy.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String storeFile(MultipartFile file, String subDir) {
        try {
            if (file.isEmpty()) {
                throw new BadRequestException("Файл пуст");
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String filename = UUID.randomUUID() + extension;

            Path uploadPath = Paths.get(uploadDir, subDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return baseUrl + "/files/" + subDir + "/" + filename;

        } catch (IOException e) {
            log.error("Ошибка при сохранении файла", e);
            throw new RuntimeException("Ошибка при сохранении файла", e);
        }
    }
}
