/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin

import com.google.gson.GsonBuilder
import com.mashape.unirest.http.Unirest
import io.javalin.http.context.body
import io.javalin.plugin.json.FromJsonMapper
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JavalinJson
import io.javalin.plugin.json.ToJsonMapper
import io.javalin.testing.NonSerializableObject
import io.javalin.testing.SerializableObject
import io.javalin.testing.TestUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TestJson {

    @Test
    fun `default mapper maps object to json`() = TestUtil.test { app, http ->
        app.get("/hello") { ctx -> ctx.json(SerializableObject()) }
        assertThat(http.getBody("/hello")).isEqualTo("""{"value1":"FirstValue","value2":"SecondValue"}""")
    }

    @Test
    fun `default mapper treats strings as already being json`() = TestUtil.test { app, http ->
        app.get("/hello") { ctx -> ctx.json("ok") }
        assertThat(http.getBody("/hello")).isEqualTo("ok")
    }

    @Test
    fun `json-mapper throws when mapping unmappable object to json`() = TestUtil.test { app, http ->
        app.get("/hello") { ctx -> ctx.json(NonSerializableObject()) }
        assertThat(http.get("/hello").status).isEqualTo(500)
        assertThat(http.getBody("/hello")).isEqualTo("""{
                |    "title": "Internal server error",
                |    "status": 500,
                |    "type": "https://javalin.io/documentation#internalservererrorresponse",
                |    "details": {}
                |}""".trimMargin())
    }

    @Test
    fun `json-mapper maps json to object`() = TestUtil.test { app, http ->
        app.post("/hello") { ctx ->
            ctx.body<SerializableObject>()
            ctx.result("success")
        }
        val jsonString = JavalinJackson.toJson(SerializableObject())
        assertThat(http.post("/hello").body(jsonString).asString().body).isEqualTo("success")
    }

    @Test
    fun `invalid json is mapped to BadRequestResponse`() = TestUtil.test { app, http ->
        app.get("/hello") { it.body<NonSerializableObject>() }
        assertThat(http.get("/hello").status).isEqualTo(400)
        val response = http.getBody("/hello").replace("\\s".toRegex(), "")
        assertThat(response).isEqualTo("""{"NonSerializableObject":[{"message":"DESERIALIZATION_FAILED","args":{},"value":""}]}""")
    }

    @Test
    fun `custom silly toJsonMapper works`() = TestUtil.test { app, http ->
        JavalinJson.toJsonMapper = object : ToJsonMapper {
            override fun map(obj: Any) = "Silly mapper"
        }
        app.get("/") { ctx -> ctx.json("Test") }
        assertThat(http.getBody("/")).isEqualTo("Silly mapper")
    }

    @Test
    fun `custom gson toJsonMapper mapper works`() = TestUtil.test { app, http ->
        val gson = GsonBuilder().create()
        JavalinJson.toJsonMapper = object : ToJsonMapper {
            override fun map(obj: Any) = gson.toJson(obj)
        }
        app.get("/") { ctx -> ctx.json(SerializableObject()) }
        assertThat(http.getBody("/")).isEqualTo(gson.toJson(SerializableObject()))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `custom silly fromJsonMapper works`() = TestUtil.test { app, http ->
        val sillyString = "Silly string"
        JavalinJson.fromJsonMapper = object : FromJsonMapper {
            override fun <T> map(json: String, targetClass: Class<T>) = sillyString as T
        }
        app.post("/") { ctx ->
            if (sillyString == ctx.bodyAsClass(String::class.java)) {
                ctx.result(sillyString)
            }
        }
        assertThat(Unirest.post("${http.origin}/").body("{}").asString().body).isEqualTo(sillyString)
    }

    @Test
    fun `custom gson fromJsonMapper works`() = TestUtil.test { app, http ->
        val gson = GsonBuilder().create()
        JavalinJson.fromJsonMapper = object : FromJsonMapper {
            override fun <T> map(json: String, targetClass: Class<T>) = gson.fromJson(json, targetClass)
        }
        app.post("/") { ctx ->
            ctx.bodyAsClass(SerializableObject::class.java)
            ctx.result("success")
        }
        assertThat(http.post("/").body(gson.toJson(SerializableObject())).asString().body).isEqualTo("success")
    }

    @Test
    fun `can use JavalinJson as an object-mapper`() {
        val mapped = JavalinJson.toJson(SerializableObject())
        val mappedBack = JavalinJson.fromJson(mapped, SerializableObject::class.java)
        assertThat(SerializableObject().value1).isEqualTo(mappedBack.value1)
        assertThat(SerializableObject().value2).isEqualTo(mappedBack.value2)
    }

    data class SerializableDataClass(val value1: String, val value2: String)

    @Test
    fun `can use JavalinJson with a custom object-mapper on a kotlin data class`() {
        val jacksonKtEnabledObjectMapper = JavalinJackson.defaultObjectMapper()
        JavalinJackson.configure(jacksonKtEnabledObjectMapper) // override mapper
        val mapped = JavalinJson.toJson(SerializableDataClass("First value", "Second value"))
        val mappedBack = JavalinJson.fromJson(mapped, SerializableDataClass::class.java)
        assertThat("First value").isEqualTo(mappedBack.value1)
        assertThat("Second value").isEqualTo(mappedBack.value2)
    }

}
