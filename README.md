# Using Google Protocol Buffers for High Speed REST



Spring Framework 4.1 introduced new support for [Google Protocol Buffers](https://developers.google.com/protocol-buffers/). From the website:

> Protocol buffers are Google's language-neutral, platform-neutral, extensible mechanism for serializing structured data – think XML, but smaller, faster, and simpler. You define how you want your data to be structured once, then you can use special generated source code to easily write and read your structured data to and from a variety of data streams and using a variety of languages...

Google uses Protocol Buffers extensively in their own, internal, service-centric architecture.

A  `.proto` document describes the types (_messages_) to be encoded and  contains a definition language that should be familiar to anyone who's used C `struct`s. In the document, you define types, fields in those types, and their ordering (memory offsets!) in the type relative to each other.

The `.proto` files aren't implementations - they're declarative descriptions of messages that may be conveyed over the wire. They can prescribe and validate constraints - the type of a given field, or the cardinatlity of that field - on the messages that are encoded and decoded.  You must use the Protobuf compiler to generate the appropriate client for  your language of choice.

You can use Google Protocol Buffers anyway you like, but in this post we'll look at using it as a way to encode REST service payloads. This approach is powerful: you can use content-negotiation to serve high speed  Protocol Buffer payloads to the clients (in any number of languages) that accept it, and something more conventional like JSON for those that don't.

Protocol Buffer messages offer a number of improvements over typical JSON-encoded messages, particularly in a polyglot system where microservices are implemented in various technologies but need to be able to reason about communication between services in a consistant, long-term   manner.  

Protocol Buffers are several nice features that promote stable APIs:

  - Protocol Buffers offer backward compatibility for free. Each field is numbered in a Protocol Buffer, so you don't have to change the behavior of the code going forward to maintain backward compatability with older clients. Clients that don't know about new fields won't bother trying to parse them.
  - Protocol Buffers provide a natural place to specify validation using the  `required`, `optional`, and `repeated` keywords. Each client enforces these constraints in their own way.
  - Protocol Buffers are polyglot, and [work with all manner of technologies](https://developers.google.com/protocol-buffers/docs/reference/other). In the example code for this blog alone there is a Ruby, Python and Java client for the Java service demonstrated. It's just a matter of using one of the _numerous_ supported compilers.


You might think that you could just use Java's inbuilt serialization mechanism in a homogeneous service environment but, as the Protocol Buffers team were quick to point out whent hey first introduced the technology, there are some problems even with that. Java language luminary Josh Bloch's epic tome, _Effective Java_, on page 213, provides further details.

Let's first look at our `.proto` document:

```json
package demo;

option java_package = "demo";
option java_outer_classname = "CustomerProtos";

message Customer {
    required int32 id = 1;
    required string firstName = 2;
    required string lastName = 3;

    enum EmailType {
        PRIVATE = 1;
        PROFESSIONAL = 2;
    }

    message EmailAddress {
        required string email = 1;
        optional EmailType type = 2 [default = PROFESSIONAL];
    }

    repeated EmailAddress email = 5;
}

message Organization {
    required string name = 1;
    repeated Customer customer = 2;
}
```

You then pass this definition to the `protoc` compiler and specify the output type, like this:

```sh
protoc -I=$IN_DIR --java_out=$OUT_DIR $IN_DIR/customer.proto
```

Here's the little Bash script I put together to code-generate my various clients:

```sh
#!/usr/bin/env bash


SRC_DIR=`pwd`
DST_DIR=`pwd`/../src/main/

echo source:            $SRC_DIR
echo destination root:  $DST_DIR

function ensure_implementations(){

    # Ruby and Go aren't natively supported it seems
    # Java and Python are

    gem list | grep ruby-protocol-buffers || sudo gem install ruby-protocol-buffers
    go get -u github.com/golang/protobuf/{proto,protoc-gen-go}
}

function gen(){
    D=$1
    echo $D
    OUT=$DST_DIR/$D
    mkdir -p $OUT
    protoc -I=$SRC_DIR --${D}_out=$OUT $SRC_DIR/customer.proto
}

ensure_implementations

gen java
gen python
gen ruby
```

This will generate the appropriate client classes in the `src/main/{java,ruby,python}` folders. Let's first look at the Spring MVC REST service itself.

## A Spring MVC REST Service

In our example, we'll register an instance of Spring framework 4.1's [`org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter`](http://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/converter/protobuf/ProtobufHttpMessageConverter.html). This type is an `HttpMessageConverter`. `HttpMessageConverter`s encode and decode the requests and responses in REST service calls. They're usually activated after some sort of content negotiation has occurred: if the client specifies `Accept: application/x-protobuf`, for example, then our REST service will send back the Protocol Buffer-encoded response.


```java
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
```

Most of this code is pretty straightforward. It's  a Spring Boot application. Spring Boot automatically registers `HttpMessageConverter` beans so we need only define the `ProtobufHttpMessageConverter` bean and it gets configured appropriately. The `@Configuration` class seeds some dummy date and a mock `CustomerRepository` object. I won't reproduce the Java type for our Protocol Buffer, `demo/CustomerProtos.java`, here as it is code-generated bit twiddling and parsing code; not all that interesting to read. One convenience is that the Java implementation automatically provides _builder_ methods for quickly creating instances of these types in Java.

The code-generated types are dumb `struct` like objects. They're suitable for use as DTOs, but should not be used as the basis for your API. Do _not_ extend them using Java inheritance to introduce new functionality;  it'll break the implementation and it's bad OOP practice, anyway. If you want to keep things cleaner, simply wrapt and adapt them as appropriate, perhaps handling conversion from an ORM entity to the Protocol Buffer client type as appropriate in that wrapper.

`HttpMessageConverter`s may also be used with Spring's REST client, the `RestTemplate`. Here's the appropriate Java-language unit test:

```java
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
```

Things just work as you'd expect, not only in Java and Spring, but also in Ruby and Python. For completeness, here is a simple client using Ruby (client types omitted):

```ruby
#!/usr/bin/env ruby

require './customer.pb'
require 'net/http'
require 'uri'

uri = URI.parse('http://localhost:8080/customers/3')
body = Net::HTTP.get(uri)
puts Demo::Customer.parse(body)
```

..and here's a client in Python (client types omitted):

```python

#!/usr/bin/env python

import urllib
import customer_pb2

if __name__ == '__main__':
    customer = customer_pb2.Customer()
    customers_read = urllib.urlopen('http://localhost:8080/customers/1').read()
    customer.ParseFromString(customers_read)
    print customer

```

## Where to go from Here
If you want _very_ high speed message encoding that works with multiple languages, Protocol Buffers are a compelling option. There are other encoding technologies like [Avro](https://avro.apache.org/) or [Thrift](https://thrift.apache.org/), but none nearly so mature and entrenched as Protocol Buffers. You don't necessarily need to use Protocol Buffers with REST, either. You could plug it into some sort of RPC service, if that's your style. There are almost as many client implementations as there are buildpacks for Cloud Foundry - so you could run almost anything on Cloud Foundry and enjoy the same high speed, consistent messaging across all your services!
