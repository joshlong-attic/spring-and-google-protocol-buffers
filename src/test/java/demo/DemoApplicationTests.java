package demo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = DemoApplication.class)
@WebAppConfiguration
@IntegrationTest
public class DemoApplicationTests {

    @Configuration
    public static class RestClientConfiguration {

        @Bean
        RestTemplate restTemplate(ProtobufHttpMessageConverter hmc) {
            return new RestTemplate(Arrays.asList(hmc));
        }

        @Bean
        ProtobufHttpMessageConverter protobufHttpMessageConverter() {
            return new ProtobufHttpMessageConverter();
        }
    }

    @Autowired
    private RestTemplate restTemplate;

    private int port = 8080;

    @Test
    public void contextLoaded() {

        ResponseEntity<CustomerProtos.Customer> customer = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/customers/2", CustomerProtos.Customer.class);

        System.out.println("customer retrieved: " + customer.toString());

    }

}
