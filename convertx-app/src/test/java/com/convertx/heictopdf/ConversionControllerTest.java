package com.convertx.heictopdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversionControllerTest {

    private MockMvc mockMvc;

    private FileToPdfConversionService conversionService;

    @BeforeEach
    void setUp() {
        this.conversionService = mock(FileToPdfConversionService.class);
        this.mockMvc = MockMvcBuilders.standaloneSetup(new ConversionController(this.conversionService)).build();
    }

    @Test
    void shouldReturnPdfAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "data".getBytes()
        );

        given(conversionService.convert(any())).willReturn("pdf".getBytes());

        mockMvc.perform(multipart("/api/convert/to-pdf").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"notes.pdf\""));
    }

    @Test
    void shouldReturnCompressedPdfAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "pdf".getBytes()
        );

        given(conversionService.compressPdf(any(), anyInt())).willReturn("pdf".getBytes());

        mockMvc.perform(multipart("/api/pdf/compress")
                        .file(file)
                        .param("targetPercentage", "50"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"source-compressed-50.pdf\""));
    }

    @Test
    void shouldReturnMergedPdfAttachment() throws Exception {
        MockMultipartFile first = new MockMultipartFile(
                "files",
                "first.pdf",
                "application/pdf",
                "pdf1".getBytes()
        );
        MockMultipartFile second = new MockMultipartFile(
                "files",
                "second.pdf",
                "application/pdf",
                "pdf2".getBytes()
        );

        given(conversionService.mergePdfs(anyList())).willReturn("pdf".getBytes());

        mockMvc.perform(multipart("/api/pdf/merge")
                        .file(first)
                        .file(second))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"merged.pdf\""));
    }
}
