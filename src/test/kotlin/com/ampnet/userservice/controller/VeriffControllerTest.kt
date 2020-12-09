package com.ampnet.userservice.controller

import com.ampnet.userservice.config.ApplicationProperties
import com.ampnet.userservice.persistence.model.User
import com.ampnet.userservice.persistence.model.VeriffDecision
import com.ampnet.userservice.persistence.model.VeriffSession
import com.ampnet.userservice.persistence.model.VeriffSessionState
import com.ampnet.userservice.persistence.repository.VeriffDecisionRepository
import com.ampnet.userservice.persistence.repository.VeriffSessionRepository
import com.ampnet.userservice.security.WithMockCrowdfundUser
import com.ampnet.userservice.service.pojo.ServiceVerificationResponse
import com.ampnet.userservice.service.pojo.VeriffStatus
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import java.time.ZonedDateTime
import java.util.UUID

class VeriffControllerTest : ControllerTestBase() {

    @Autowired
    private lateinit var applicationProperties: ApplicationProperties

    @Autowired
    private lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var veriffSessionRepository: VeriffSessionRepository

    @Autowired
    private lateinit var veriffDecisionRepository: VeriffDecisionRepository

    private val veriffPath = "/veriff"
    private val xClientHeader = "X-AUTH-CLIENT"
    private val xSignature = "X-SIGNATURE"

    private lateinit var testContext: TestContext
    private lateinit var mockServer: MockRestServiceServer

    @BeforeEach
    fun init() {
        testContext = TestContext()
        mockServer = MockRestServiceServer.createServer(restTemplate)
    }

    @Test
    fun mustStoreUserInfoFromVeriff() {
        suppose("User has no user info") {
            databaseCleanerService.deleteAllUsers()
            databaseCleanerService.deleteAllUserInfos()
            testContext.user =
                createUser("veriff@email.com", uuid = UUID.fromString("5750f893-29fa-4910-8304-62f834338f47"))
        }

        verify("Controller will accept valid data") {
            val request = getResourceAsText("/veriff/response-with-vendor-data.json")
            mockMvc.perform(
                post("$veriffPath/webhook/decision")
                    .content(request)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(xClientHeader, applicationProperties.veriff.apiKey)
                    .header(xSignature, "0b65acb46ddb2a881f5adf742c03b81290ec783db3ef425d13a2c2448f400f64")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }
        verify("User info is stored") {
            val userInfo = userInfoRepository.findBySessionId("12df6045-3846-3e45-946a-14fa6136d78b")
            assertThat(userInfo).isPresent
            assertThat(userInfo.get().connected).isTrue()
        }
    }

    @Test
    fun mustHandleVeriffWebhookEvent() {
        suppose("User has no user info") {
            databaseCleanerService.deleteAllUsers()
            databaseCleanerService.deleteAllUserInfos()
            databaseCleanerService.deleteAllVeriffSessions()
            testContext.user =
                createUser("event@email.com", uuid = UUID.fromString("2652972e-2dfd-428a-93b9-3b283a0a754c"))
        }
        suppose("User has veriff session") {
            val veriffSession = VeriffSession(
                "cbb238c6-51a0-482b-bd1a-42a2e0b0ff1c",
                testContext.user.uuid,
                "https://alchemy.veriff.com/v/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                testContext.user.uuid.toString(),
                "https://alchemy.veriff.com/",
                "created",
                false,
                ZonedDateTime.now(),
                VeriffSessionState.CREATED
            )
            testContext.veriffSession = veriffSessionRepository.save(veriffSession)
        }

        verify("Controller will accept submitted event data") {
            val request = getResourceAsText("/veriff/response-event-submitted.json")
            mockMvc.perform(
                post("$veriffPath/webhook/event")
                    .content(request)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(xClientHeader, applicationProperties.veriff.apiKey)
                    .header(xSignature, "bf3da6e9aa47e6be208fec283097a5bcbdb2066dcb58f0d7c9879637700f013f")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }
        verify("Veriff session is updated") {
            val veriffSession = veriffSessionRepository.findById(testContext.veriffSession.id)
            assertThat(veriffSession.isPresent)
            assertThat(veriffSession.get().state).isEqualTo(VeriffSessionState.SUBMITTED)
        }
    }

    @Test
    fun mustReturnBadRequestForInvalidSignature() {
        verify("Controller will return bad request for invalid signature header data") {
            val request = getResourceAsText("/veriff/response-event-submitted.json")
            mockMvc.perform(
                post("$veriffPath/webhook/event")
                    .content(request)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(xClientHeader, applicationProperties.veriff.apiKey)
                    .header(xSignature, "invalid-signature")
            )
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
        }
    }

    @Test
    fun mustReturnBadRequestForInvalidClient() {
        verify("Controller will return bad request for invalid client header data") {
            val request = getResourceAsText("/veriff/response-with-vendor-data.json")
            mockMvc.perform(
                post("$veriffPath/webhook/decision")
                    .content(request)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(xClientHeader, "invalid-api-key")
                    .header(xSignature, "0b65acb46ddb2a881f5adf742c03b81290ec783db3ef425d13a2c2448f400f64")
            )
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
        }
    }

    @Test
    @WithMockCrowdfundUser(uuid = "4c2c2950-7a20-4fd7-b37f-f1d63a8211b4")
    fun mustReturnVeriffSession() {
        suppose("User has an account") {
            databaseCleanerService.deleteAllUsers()
            databaseCleanerService.deleteAllUserInfos()
            databaseCleanerService.deleteAllVeriffSessions()
            testContext.user =
                createUser("resubmission@email.com", uuid = UUID.fromString("4c2c2950-7a20-4fd7-b37f-f1d63a8211b4"))
            val veriffSession = VeriffSession(
                "44927492-8799-406e-8076-933bc9164ebc",
                testContext.user.uuid,
                "https://alchemy.veriff.com/v/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                testContext.user.uuid.toString(),
                "https://alchemy.veriff.com/",
                "created",
                false,
                ZonedDateTime.now(),
                VeriffSessionState.SUBMITTED
            )
            testContext.veriffSession = veriffSessionRepository.save(veriffSession)
        }
        suppose("Veriff posted declined decision") {
            databaseCleanerService.deleteAllVeriffDecisions()
            val decision = VeriffDecision(
                testContext.veriffSession.id,
                VeriffStatus.declined,
                9102,
                "Physical document not used",
                101,
                "2020-12-04T10:45:37.907Z",
                "2020-12-04T10:45:31.000Z",
                ZonedDateTime.now()
            )
            veriffDecisionRepository.save(decision)
        }
        suppose("Veriff will return new session") {
            val response = getResourceAsText("/veriff/response-new-session.json")
            mockVeriffResponse(response, HttpMethod.POST, "/v1/sessions/")
        }

        verify("Controller will return new veriff session") {
            val result = mockMvc.perform(get("$veriffPath/session"))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
            val veriffResponse: ServiceVerificationResponse = objectMapper.readValue(result.response.contentAsString)
            assertThat(veriffResponse.decision).isNotNull
            assertThat(veriffResponse.verificationUrl).isEqualTo("https://alchemy.veriff.com/v/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new-url")
        }
    }

    private fun mockVeriffResponse(body: String, method: HttpMethod, path: String) {
        val status = MockRestResponseCreators.withStatus(HttpStatus.OK)
        mockServer.expect(
            ExpectedCount.once(),
            MockRestRequestMatchers.requestTo(applicationProperties.veriff.baseUrl + path)
        )
            .andExpect(MockRestRequestMatchers.method(method))
            .andRespond(status.body(body).contentType(MediaType.APPLICATION_JSON))
    }

    private class TestContext {
        lateinit var user: User
        lateinit var veriffSession: VeriffSession
    }
}
