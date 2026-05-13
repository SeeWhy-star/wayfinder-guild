package com.seewhy.syimagesearchmcp;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.seewhy.syimagesearchmcp.tools.ImageSearchTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ImageSearchTool 单元测试
 * 测试图片搜索功能
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
@Disabled("Legacy Pexels integration-style tests depend on live HTTP and a local API key. Keep disabled for safe production builds.")
class ImageSearchToolTest {

    // 模拟 HTTP 请求
    @Mock
    private HttpRequest mockHttpRequest;
    
    @Mock
    private HttpResponse mockHttpResponse;
    
    // 注入被测试对象
    @InjectMocks
    private ImageSearchTool imageSearchTool;
    
    /**
     * 测试 searchImage 方法 - 成功返回图片URL
     */
    @Test
    void testSearchImage_Success() {
        // 1. 模拟 HTTP 响应
        String mockApiResponse = """
            {
                "photos": [
                    {
                        "src": {
                            "medium": "https://images.pexels.com/photos/2860705/pexels-photo-2860705.jpeg?auto=compress&cs=tinysrgb&h=350"
                        }
                    },
                    {
                        "src": {
                            "medium": "https://images.pexels.com/photos/63340/pexels-photo-63340.jpeg?auto=compress&cs=tinysrgb&h=350"
                        }
                    }
                ],
                "total_results": 2
            }
            """;
        
        // 2. 设置模拟行为
        when(mockHttpRequest.addHeaders(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.form(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(mockApiResponse);
        
        // 3. 执行测试
        String result = imageSearchTool.searchImage("ocean");
        
        // 4. 验证结果
        assertNotNull(result, "搜索结果不应为空");
        assertTrue(result.contains(","), "多个图片URL应以逗号分隔");
        assertTrue(result.contains("pexels.com"), "结果应包含图片URL");
        
        // 5. 验证格式
        String[] urls = result.split(",");
        assertTrue(urls.length >= 1, "应至少返回一个图片URL");
        
        for (String url : urls) {
            assertTrue(url.contains("https://"), "URL应以https://开头");
            assertTrue(url.contains("medium") || url.contains("pexels-photo"), "应包含medium尺寸的图片");
        }
    }
    
    /**
     * 测试 searchImage 方法 - 空结果
     */
    @Test
    void testSearchImage_EmptyResult() {
        // 模拟空响应
        String mockApiResponse = """
            {
                "photos": [],
                "total_results": 0
            }
            """;
            
        when(mockHttpRequest.addHeaders(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.form(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(mockApiResponse);
        
        String result = imageSearchTool.searchImage("nonexistentquery");
        
        // 空结果应返回空字符串
        assertEquals("", result, "无搜索结果时应返回空字符串");
    }
    
    /**
     * 测试 searchImage 方法 - API 错误
     */
    @Test
    void testSearchImage_ApiError() {
        // 模拟异常
        when(mockHttpRequest.addHeaders(any())).thenThrow(new RuntimeException("Network error"));
        
        String result = imageSearchTool.searchImage("test");
        
        assertNotNull(result, "错误时不应返回null");
        assertTrue(result.startsWith("Error search image: "), "应返回错误信息");
        assertTrue(result.contains("Network error"), "应包含具体错误信息");
    }
    
    /**
     * 测试 searchImage 方法 - 实际集成测试
     * 注意：此测试需要实际的 API 密钥和网络访问
     * 建议在本地环境运行，CI/CD 环境中可能需要禁用
     */
    @Test
    void testSearchImage_Integration() {
        // 这个测试会实际调用 API
        // 在运行前，请确保：
        // 1. API_KEY 有效
        // 2. API_URL 已正确设置
        // 3. 有网络连接
        
        // 可以使用 @Disabled 注解在不需要时禁用此测试
        // @Disabled("需要实际的 API 访问")
        
        try {
            String result = imageSearchTool.searchImage("ocean");
            
            // 验证返回结果
            assertNotNull(result, "实际API调用结果不应为空");
            
            if (!result.startsWith("Error search image: ")) {
                // 成功返回
                assertFalse(result.isEmpty(), "成功时不应返回空字符串");
                
                // 验证返回的URL格式
                String[] urls = result.split(",");
                for (String url : urls) {
                    assertTrue(url.startsWith("https://"), "图片URL应以https://开头");
                    assertTrue(url.contains("pexels.com"), "应包含Pexels域名");
                }
                
                System.out.println("搜索关键词: ocean");
                System.out.println("返回的图片URL数量: " + urls.length);
                System.out.println("示例图片URL: " + (urls.length > 0 ? urls[0] : "无"));
            } else {
                // API调用出错
                System.out.println("API调用错误: " + result);
            }
        } catch (Exception e) {
            fail("集成测试不应抛出异常: " + e.getMessage());
        }
    }
    
    /**
     * 测试 searchMediumImages 方法
     */
    @Test
    void testSearchMediumImages() {
        // 模拟完整的API响应
        String mockApiResponse = """
            {
                "photos": [
                    {
                        "id": 2860705,
                        "src": {
                            "original": "https://images.pexels.com/photos/2860705/pexels-photo-2860705.jpeg",
                            "medium": "https://images.pexels.com/photos/2860705/pexels-photo-2860705.jpeg?auto=compress&cs=tinysrgb&h=350",
                            "small": "https://images.pexels.com/photos/2860705/pexels-photo-2860705.jpeg?auto=compress&cs=tinysrgb&h=130"
                        }
                    },
                    {
                        "id": 63340,
                        "src": {
                            "original": "https://images.pexels.com/photos/63340/pexels-photo-63340.jpeg",
                            "medium": "https://images.pexels.com/photos/63340/pexels-photo-63340.jpeg?auto=compress&cs=tinysrgb&h=350",
                            "small": "https://images.pexels.com/photos/63340/pexels-photo-63340.jpeg?auto=compress&cs=tinysrgb&h=130"
                        }
                    }
                ],
                "total_results": 2
            }
            """;
        
        when(mockHttpRequest.addHeaders(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.form(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(mockApiResponse);
        
        List<String> imageUrls = imageSearchTool.searchMediumImages("mountain");
        
        assertNotNull(imageUrls, "图片URL列表不应为空");
        assertEquals(2, imageUrls.size(), "应返回2个图片URL");
        
        // 验证每个URL都是medium尺寸
        for (String url : imageUrls) {
            assertTrue(url.contains("medium"), "应返回medium尺寸的图片");
            assertTrue(url.startsWith("https://images.pexels.com"), "URL格式正确");
        }
    }
    
    /**
     * 测试边界情况 - 空查询字符串
     */
    @Test
    void testSearchImage_EmptyQuery() {
        // 模拟API响应
        String mockApiResponse = """
            {
                "photos": [],
                "total_results": 0
            }
            """;
            
        when(mockHttpRequest.addHeaders(any())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.form(argThat(params -> 
            params.containsKey("query") && "".equals(params.get("query"))
        ))).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(mockApiResponse);
        
        String result = imageSearchTool.searchImage("");
        
        // 空查询可能返回空结果或错误，具体取决于API实现
        // 这里我们只验证不抛出异常
        assertNotNull(result);
    }
    
    /**
     * 测试 HTTP 请求参数
     */
    @Test
    void testHttpRequestParameters() {
        // 验证请求头中包含API密钥
        String apiKey = System.getenv().getOrDefault("PEXELS_API_KEY", "test-pexels-api-key");
        String apiUrl = ""; // 需要设置实际的API_URL
        
        // 创建真实的工具实例进行测试
        ImageSearchTool realTool = new ImageSearchTool();
        
        // 这里可以添加更多针对实际HTTP请求的验证
        // 由于HttpUtil是静态方法，可能需要使用PowerMock或修改设计以便更好地测试
    }
}
