#!/usr/bin/env python

import urllib
import customer_pb2

if __name__ == '__main__':
    customer = customer_pb2.Customer()
    customers_read = urllib.urlopen('http://localhost:8080/customers/1').read()
    customer.ParseFromString(customers_read)
    print customer
