/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.it;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Address;
import org.kie.kogito.Person;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;
import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class PersistenceTest {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final String PROCESS_ID = "hello";
    public static final String PROCESS_EMBEDDED_ID = "embedded";
    public static final String PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID = "MultipleInstanceEmbeddedSubProcess";
    public static final String PROCESS_MULTIPLE_INSTANCES_ID = "MultipleInstanceSubProcess";
    public static final String PROCESS_ASYNC_WIH = "AsyncWIH";

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void testPersistence() {
        Person person = new Person("Name", 10, BigDecimal.valueOf(5.0), Instant.now().truncatedTo(ChronoUnit.MILLIS), ZonedDateTime.now(ZoneOffset.UTC));
        Person relative = new Person("relative", 5, BigDecimal.valueOf(5.0), Instant.now().truncatedTo(ChronoUnit.MILLIS), ZonedDateTime.now(ZoneOffset.UTC));
        person.setRelatives(new Person[] { relative });
        person.setAddresses(asList(new Address("Brisbane"), new Address("Sydney")));
        final String pid = given().contentType(ContentType.JSON)
                .when()
                .body(Map.of("var1", "Tiago", "person", person))
                .post("/{processId}", PROCESS_ID)
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Tiago"))
                .body("var2", equalTo("Hello Tiago! Script"))
                .body("person.name", equalTo(person.getName()))
                .body("person.age", equalTo(person.getAge()))
                .body("person.score", equalTo(person.getScore().floatValue()))
                .body("person.created", equalTo(DateTimeFormatter.ISO_INSTANT.format(person.getCreated())))
                .body("person.updated", equalTo(person.getUpdated().format(ISO_ZONED_DATE_TIME)))
                .body("person.relatives.size()", equalTo(1))
                .body("person.relatives[0].name", equalTo(relative.getName()))
                .body("person.relatives[0].age", equalTo(relative.getAge()))
                .body("person.addresses.size()", equalTo(person.getAddresses().size()))
                .body("person.addresses[0].city", equalTo(person.getAddresses().get(0).getCity()))
                .body("person.addresses[1].city", equalTo(person.getAddresses().get(1).getCity()))
                .body("person.addresses[1].type", equalTo(person.getAddresses().get(1).getType().name()))
                .body("person.addresses[1].status", equalTo(person.getAddresses().get(1).getStatus().name()))
                .extract()
                .path("id");

        final String createdPid = given().contentType(ContentType.JSON)
                .when()
                .get("/{processId}/{id}", PROCESS_ID, pid)
                .then()
                .statusCode(200)
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Tiago"))
                .body("var2", equalTo("Hello Tiago! Script"))
                .body("person.name", equalTo(person.getName()))
                .body("person.age", equalTo(person.getAge()))
                .body("person.score", equalTo(person.getScore().floatValue()))
                .body("person.created", equalTo(DateTimeFormatter.ISO_INSTANT.format(person.getCreated().truncatedTo(ChronoUnit.MILLIS))))
                .body("person.updated", equalTo(person.getUpdated().format(ISO_ZONED_DATE_TIME)))
                .body("person.relatives.size()", equalTo(1))
                .body("person.relatives[0].name", equalTo(relative.getName()))
                .body("person.relatives[0].age", equalTo(relative.getAge()))
                .body("person.addresses.size()", equalTo(person.getAddresses().size()))
                .body("person.addresses[0].city", equalTo(person.getAddresses().get(0).getCity()))
                .body("person.addresses[1].city", equalTo(person.getAddresses().get(1).getCity()))
                .body("person.addresses[1].type", equalTo(person.getAddresses().get(1).getType().name()))
                .body("person.addresses[1].status", equalTo(person.getAddresses().get(1).getStatus().name()))
                .extract()
                .path("id");

        assertEquals(createdPid, pid);

        given().contentType(ContentType.JSON)
                .when()
                .get("/greetings/{id}", pid)
                .then()
                .statusCode(404);
    }

    @Test
    void testHealthCheck() {
        given().contentType(ContentType.JSON)
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void testEmbeddedProcess() {
        final String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_EMBEDDED_ID)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        String taskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("pId", pId)
                .pathParam("processId", PROCESS_EMBEDDED_ID)
                .when()
                .get("/{processId}/{pId}/tasks")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");

        given().contentType(ContentType.JSON)
                .pathParam("pId", pId)
                .pathParam("taskId", taskId)
                .pathParam("processId", PROCESS_EMBEDDED_ID)
                .queryParam("user", "test")
                .queryParam("group", "test")
                .body("{}")
                .when()
                .post("/{processId}/{pId}/Task/{taskId}/phases/complete")
                .then()
                .statusCode(200);

    }

    @Test
    void testMultipleEmbeddedInstance() {
        String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        String taskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .pathParam("pId", pId)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .when()
                .get("/{processId}/{pId}/tasks")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");

        given().contentType(ContentType.JSON)
                .pathParam("pId", pId)
                .pathParam("taskId", taskId)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .queryParam("user", "admin")
                .queryParam("group", "admin")
                .body("{}")
                .when()
                .post("/{processId}/{pId}/Task/{taskId}/phases/complete")
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .pathParam("pId", pId)
                .when()
                .get("/{processId}/{pId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testMultipleInstance() {
        String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_ID)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_ID)
                .pathParam("pId", pId)
                .when()
                .get("/{processId}/{pId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testAsyncWIH() {
        String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_ASYNC_WIH)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .pathParam("processId", PROCESS_ASYNC_WIH)
                        .pathParam("pId", pId)
                        .when()
                        .get("/{processId}/{pId}")
                        .then()
                        .statusCode(404));
    }
}
