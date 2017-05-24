package opViewLineBot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Package : opViewLineBot
 * --
 * Description :
 * Author : jasonlin
 * Date : 2017/1/4
 */
@Configuration
@Slf4j
public class LineBotWebMvcConfigurer extends WebMvcConfigurerAdapter {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String downloadedContentUri = LineBotApplication.downloadedContentDir.toUri().toASCIIString();
        log.info("downloaded dir : {}", downloadedContentUri);
        registry.addResourceHandler("/downloaded/**")
                .addResourceLocations(downloadedContentUri);
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
