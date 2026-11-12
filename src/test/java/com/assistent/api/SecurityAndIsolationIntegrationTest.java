package com.assistent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Fluxo real: register/login com JWT, CurrentUser e isolamento entre workspaces (404, nunca 403). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndIsolationIntegrationTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", PostgresTestSupport.POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", PostgresTestSupport.POSTGRES::getUsername);
		registry.add("spring.datasource.password", PostgresTestSupport.POSTGRES::getPassword);
	}

	@Autowired MockMvc mockMvc;
	@Autowired JsonMapper jsonMapper;

	private String register(String workspace, String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"workspaceName\":\"%s\",\"name\":\"Ana\",\"email\":\"%s\",\"password\":\"senha-segura\"}"
								.formatted(workspace, email)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).path("accessToken").asString();
	}

	@Test
	void protectedEndpoint_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void login_afterRegister_returnsTokenThatIdentifiesTheUser() throws Exception {
		register("Acme", "login@acme.com");

		String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"login@acme.com\",\"password\":\"senha-segura\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = jsonMapper.readTree(body).path("accessToken").asString();

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("login@acme.com"));
	}

	@Test
	void login_wrongPassword_returns401() throws Exception {
		register("Acme2", "wrong@acme.com");

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"wrong@acme.com\",\"password\":\"errada-errada\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void register_duplicateEmail_returns409() throws Exception {
		register("Acme3", "dup@acme.com");

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"workspaceName\":\"Outra\",\"name\":\"Bia\",\"email\":\"dup@acme.com\",\"password\":\"senha-segura\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void document_createdInOneWorkspace_isNotFoundForAnother() throws Exception {
		String tokenA = register("Empresa A", "a@empresa.com");
		String tokenB = register("Empresa B", "b@empresa.com");

		String created = mockMvc.perform(post("/api/v1/documents").header("Authorization", "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fileName\":\"manual.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1000,\"sha256Hash\":\"%s\"}"
								.formatted("c".repeat(64))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.document.status").value("AWAITING_UPLOAD"))
				.andExpect(jsonPath("$.uploadUrl").value("http://localhost:9000/assistant"))
				.andReturn().getResponse().getContentAsString();
		String id = jsonMapper.readTree(created).path("document").path("id").asString();
		assertThat(id).isNotBlank();

		mockMvc.perform(get("/api/v1/documents/" + id).header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/documents/" + id).header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void document_sameHashInSameWorkspace_returns409WithExistingId() throws Exception {
		String token = register("Empresa C", "c@empresa.com");
		String body = "{\"fileName\":\"x.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":10,\"sha256Hash\":\"%s\"}"
				.formatted("d".repeat(64));
		mockMvc.perform(post("/api/v1/documents").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/documents").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.existingId").exists());
	}

	@Test
	void playground_conversationOfAnotherWorkspace_returns404() throws Exception {
		String tokenA = register("Empresa D", "d@empresa.com");
		String tokenB = register("Empresa E", "e@empresa.com");
		String created = mockMvc.perform(post("/api/v1/playground/conversations").header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = jsonMapper.readTree(created).path("id").asString();

		mockMvc.perform(get("/api/v1/playground/conversations/" + id + "/messages").header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/playground/conversations/" + id + "/messages").header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound());
	}
}
