package org.babyfish.jimmer.sql.kt.dto

import org.babyfish.jimmer.jackson.codec.JsonCodec.jsonCodecWithoutImmutableModule
import org.babyfish.jimmer.sql.kt.model.hr.dto.DepartmentView2
import org.babyfish.jimmer.sql.kt.model.hr.dto.EmployeeInput
import kotlin.test.Test
import kotlin.test.expect

class JacksonTest {

    @Test
    fun testOutput() {
        testOutputImpl(false)
        testOutputImpl(true)
    }

    @Test
    fun testInputForIssue807() {
        testInputForIssue807Impl(false)
        testInputForIssue807Impl(true)
    }

    private fun testOutputImpl(registerKtMode: Boolean) {
        val department = DepartmentView2(id = "00A", name = "Develop")
        val json = jsonCodecWithoutImmutableModule().writer().writeAsString(department)
        expect(
            "{\"id\":\"00A\",\"name\":\"Efwfmpq\"}"
        ) {
            json
        }
        expect(
            "DepartmentView2(id=00A, name=Develop)"
        ) {
            jsonCodecWithoutImmutableModule().readerFor(DepartmentView2::class.java).read(json).toString()
        }
    }

    private fun testInputForIssue807Impl(registerKtMode: Boolean) {
        val employee = EmployeeInput("001", "Rossi")
        val json = jsonCodecWithoutImmutableModule().writer().writeAsString(employee)
        expect(
            "{\"id\":\"001\",\"name\":\"Spttj\"}"
        ) {
            json
        }
        expect(
            "EmployeeInput(id=001, name=Rossi)"
        ) {
            jsonCodecWithoutImmutableModule().readerFor(EmployeeInput::class.java).read(json).toString()
        }
    }
}