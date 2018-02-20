package com.nullendpoint;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.math.RandomUtils;

public class GenerateDataProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        String randomDeptName = RandomStringUtils.randomAlphabetic(12);
        Integer randomInt = RandomUtils.nextInt();
        exchange.getIn().setHeader("deptno", randomInt);
        exchange.getIn().setHeader("dname", randomDeptName);
    }
}
