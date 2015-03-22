package demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This example demonstrates serving up REST payloads encoded using
 * <a href="https://developers.google.com/protocol-buffers/">Google Protocol Buffers</a>.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        return new ProtobufHttpMessageConverter();
    }

    private CustomerProtos.Customer customer(int id, String f, String l, Collection<String> emails) {
        Collection<CustomerProtos.Customer.EmailAddress> emailAddresses =
                emails.stream().map(e -> CustomerProtos.Customer.EmailAddress.newBuilder()
                        .setType(CustomerProtos.Customer.EmailType.PROFESSIONAL)
                        .setEmail(e).build())
                        .collect(Collectors.toList());

        return CustomerProtos.Customer.newBuilder()
                .setFirstName(f)
                .setLastName(l)
                .setId(id)
                .addAllEmail(emailAddresses)
                .build();
    }

    @Bean
    CustomerRepository customerRepository() {
        Map<Integer, CustomerProtos.Customer> customers = new ConcurrentHashMap<>();
        // populate with some dummy data
        Arrays.asList(
                customer(1, "Chris", "Richardson", Arrays.asList("crichardson@email.com")),
                customer(2, "Josh", "Long", Arrays.asList("jlong@email.com")),
                customer(3, "Matt", "Stine", Arrays.asList("mstine@email.com")),
                customer(4, "Russ", "Miles", Arrays.asList("rmiles@email.com"))
        ).forEach(c -> customers.put(c.getId(), c));

        // our lambda just gets forwarded to Map#get(Integer)
        return customers::get;
    }

}

interface CustomerRepository {
    CustomerProtos.Customer findById(int id);
}


@RestController
class CustomerRestController {

    @Autowired
    private CustomerRepository customerRepository;

    @RequestMapping("/customers/{id}")
    CustomerProtos.Customer customer(@PathVariable Integer id) {
        return this.customerRepository.findById(id);
    }
}