package com.futspring.backend.controller;

import com.futspring.backend.exception.AppException;
import com.futspring.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerTest {

    @TempDir
    Path tempDir;

    FileController controller;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new FileController();
        ReflectionTestUtils.setField(controller, "uploadsDir", tempDir.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void serveFile_existingJpeg() throws Exception {
        Files.write(tempDir.resolve("test.jpg"), new byte[]{(byte) 0xFF, (byte) 0xD8});

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/files/test.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void serveFile_existingPng() throws Exception {
        Files.write(tempDir.resolve("test.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/files/test.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void serveFile_notFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/files/nonexistent.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serveFile_pathTraversal() {
        // URL normalization strips "../" before MockMvc reaches the controller, so
        // we invoke the method directly to verify Fix 1's containment check.
        AppException ex = assertThrows(AppException.class,
                () -> controller.serveFile("../../etc/passwd"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
