package com.igot.karmaquest;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


/**
 * @author Mahesh RV
 * @author Ruksana
 */
@EnableJpaRepositories(basePackages = {"com.igot.karmaquest"})
@ComponentScan(basePackages = "com.igot.karmaquest")
@EntityScan("com.igot.karmaquest")
@SpringBootApplication
public class KarmaquestApplication {

	public static void main(String[] args) {
		SpringApplication.run(KarmaquestApplication.class, args);
	}

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(getClientHttpRequestFactory());
    }

    private ClientHttpRequestFactory getClientHttpRequestFactory() {
        int timeout = 45000;
        RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout).build();
        CloseableHttpClient client = HttpClientBuilder.create().setMaxConnTotal(2000).setMaxConnPerRoute(500)
                .setDefaultRequestConfig(config).build();
        HttpComponentsClientHttpRequestFactory cRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
        cRequestFactory.setReadTimeout(timeout);
        return cRequestFactory;
    }

}
