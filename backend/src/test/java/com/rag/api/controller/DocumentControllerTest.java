package com.rag.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rag.document.entity.DocumentEntity;
import com.rag.document.service.DocumentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    void listsDocuments() throws Exception {
        DocumentEntity document = new DocumentEntity("policy.txt", "TXT", 11);
        document.markStored("documents/policy.txt");
        document.markReady(3);
        when(documentService.list()).thenReturn(List.of(document));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("policy.txt"))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].chunkCount").value(3));
    }

    @Test
    void uploadsDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", "hello world".getBytes());
        DocumentEntity document = new DocumentEntity("policy.txt", "TXT", 11);
        document.markStored("documents/policy.txt");
        document.markReady(3);
        when(documentService.upload(org.mockito.ArgumentMatchers.any())).thenReturn(document);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("policy.txt"))
                .andExpect(jsonPath("$.type").value("TXT"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(3));
    }

    @Test
    void deletesDocument() throws Exception {
        mockMvc.perform(delete("/api/documents/doc-1"))
                .andExpect(status().isNoContent());

        verify(documentService).delete("doc-1");
    }

    @Test
    void returnsBadRequestForInvalidUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "policy.exe", "application/octet-stream", "bad".getBytes());
        when(documentService.upload(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("不支持的文件格式：EXE"));

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的文件格式：EXE"));
    }
}
