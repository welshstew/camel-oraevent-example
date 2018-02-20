/**
 *  Copyright 2005-2016 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package com.nullendpoint;

import oracle.jdbc.dcn.DatabaseChangeEvent;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;


import javax.sql.DataSource;
import java.sql.SQLSyntaxErrorException;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
@ImportResource({"classpath:spring/camel-context.xml"})
public class Application extends RouteBuilder {

    // must have a main method spring-boot can run
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    @ConfigurationProperties(prefix = "dataSource")
    BasicDataSource dataSource() {
        return new BasicDataSource();
    }

    @Override
    public void configure() throws Exception {

        from("timer://createTable?period=1000&repeatCount=1")
                .onException(Exception.class).continued(true)
                    .log("table already exists")
                    .end()
                .setBody(constant("CREATE TABLE DEPT(DEPTNO NUMBER,DNAME VARCHAR2(50))"))
                .to("jdbc:dataSource")
                .to("controlbus:route?routeId=pollData&action=start")
                .to("controlbus:route?routeId=insertData&action=start")
                .log("All Started!");

        from("timer://foo?period=5000").routeId("insertData").autoStartup(false)
                .process(new GenerateDataProcessor())
                .setBody(constant("INSERT INTO DEPT(deptno,dname) values (:?deptno, :?dname)"))
                .to("jdbc:dataSource?useHeadersAsParameters=true");


        from("oraevent://select * from dept?dataSource=dataSource").routeId("pollData").autoStartup(false)
                .log("Got NTFDCN: ${body}")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        DatabaseChangeEvent oracleEvent = (DatabaseChangeEvent) exchange.getIn().getBody();
                        exchange.getIn().setHeader("tableName",
                                oracleEvent.getQueryChangeDescription()[0].getTableChangeDescription()[0].getTableName());
                        exchange.getIn().setHeader("rowId",
                                oracleEvent.getQueryChangeDescription()[0].getTableChangeDescription()[0].getRowChangeDescription()[0].getRowid().stringValue());
                    }
                })
                .setBody(simple("SELECT * FROM ${header.tableName} WHERE ROWID = '${header.rowId}'"))
                .to("jdbc:dataSource")
                .log("Row which caused event: ${body}");

    }
}
