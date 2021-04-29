/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.test.*

class HttpProtocolClientGeneratorTest {
    private val commonTestContents: String
    private val writer: KotlinWriter = KotlinWriter(TestModelDefault.NAMESPACE)

    class MockProtocolMiddleware1 : ProtocolMiddleware {
        override val name: String = "MockProtocolMiddleware1"
        override fun renderConfigure(writer: KotlinWriter) {
            writer.write("configurationField1 = \"testing\"")
        }
    }

    init {
        val model = javaClass
            .classLoader
            .getResource("software/amazon/smithy/kotlin/codegen/service-generator-test-operations.smithy")!!
            .toSmithyModel()

        val ctx = model.newTestContext()
        val features: List<ProtocolMiddleware> = listOf(MockProtocolMiddleware1())
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            features,
            HttpTraitResolver(ctx.generationCtx, "application/json")
        )
        generator.render(writer)
        commonTestContents = writer.toString()
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnce("import ${TestModelDefault.NAMESPACE}.model.*")
        commonTestContents.shouldContainOnlyOnce("import ${TestModelDefault.NAMESPACE}.transform.*")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.*")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.engine.HttpClientEngineConfig")

        // test for feature imports that are added
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_SERDE_JSON.namespace}.JsonSerdeProvider")
    }

    @Test
    fun `it renders constructor`() {
        commonTestContents.shouldContainOnlyOnce("internal class DefaultTestClient(private val config: TestClient.Config) : TestClient {")
    }

    @Test
    fun `it renders properties and init`() {
        commonTestContents.shouldContainOnlyOnce("val client: SdkHttpClient")
        val expected = """
    init {
        val httpClientEngine = config.httpClientEngine ?: KtorEngine(HttpClientEngineConfig())
        client = sdkHttpClient(httpClientEngine)
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders close`() {
        val expected = """
    override fun close() {
        client.close()
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders operation bodies`() {
        val expectedBodies = listOf(
"""
    override suspend fun getFoo(input: GetFooRequest): GetFooResponse {
        val op = SdkHttpOperation.build<GetFooRequest, GetFooResponse> {
            serializer = GetFooOperationSerializer()
            deserializer = GetFooOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFoo"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun getFooNoInput(input: GetFooNoInputRequest): GetFooNoInputResponse {
        val op = SdkHttpOperation.build<GetFooNoInputRequest, GetFooNoInputResponse> {
            serializer = GetFooNoInputOperationSerializer()
            deserializer = GetFooNoInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooNoInput"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun getFooNoOutput(input: GetFooNoOutputRequest): GetFooNoOutputResponse {
        val op = SdkHttpOperation.build<GetFooNoOutputRequest, GetFooNoOutputResponse> {
            serializer = GetFooNoOutputOperationSerializer()
            deserializer = GetFooNoOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooNoOutput"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun getFooStreamingInput(input: GetFooStreamingInputRequest): GetFooStreamingInputResponse {
        val op = SdkHttpOperation.build<GetFooStreamingInputRequest, GetFooStreamingInputResponse> {
            serializer = GetFooStreamingInputOperationSerializer()
            deserializer = GetFooStreamingInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingInput"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutput(input: GetFooStreamingOutputRequest, block: suspend (GetFooStreamingOutputResponse) -> T): T {
        val op = SdkHttpOperation.build<GetFooStreamingOutputRequest, GetFooStreamingOutputResponse> {
            serializer = GetFooStreamingOutputOperationSerializer()
            deserializer = GetFooStreamingOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingOutput"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.execute(client, input, block)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutputNoInput(input: GetFooStreamingOutputNoInputRequest, block: suspend (GetFooStreamingOutputNoInputResponse) -> T): T {
        val op = SdkHttpOperation.build<GetFooStreamingOutputNoInputRequest, GetFooStreamingOutputNoInputResponse> {
            serializer = GetFooStreamingOutputNoInputOperationSerializer()
            deserializer = GetFooStreamingOutputNoInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingOutputNoInput"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.execute(client, input, block)
    }
""",
"""
    override suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingInputNoOutputRequest): GetFooStreamingInputNoOutputResponse {
        val op = SdkHttpOperation.build<GetFooStreamingInputNoOutputRequest, GetFooStreamingInputNoOutputResponse> {
            serializer = GetFooStreamingInputNoOutputOperationSerializer()
            deserializer = GetFooStreamingInputNoOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingInputNoOutput"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
        return op.roundTrip(client, input)
    }
"""
        )
        expectedBodies.forEach {
            commonTestContents.shouldContainOnlyOnceWithDiff(it)
        }
    }

    @Test
    fun `it syntactic sanity checks`() {
        // sanity check since we are testing fragments
        var openBraces = 0
        var closedBraces = 0
        var openParens = 0
        var closedParens = 0
        commonTestContents.forEach {
            when (it) {
                '{' -> openBraces++
                '}' -> closedBraces++
                '(' -> openParens++
                ')' -> closedParens++
            }
        }
        assertEquals(openBraces, closedBraces)
        assertEquals(openParens, closedParens)
    }

    @Test
    fun `it handles endpointTrait hostPrefix with label`() {
        val model = """
            @readonly
            @endpoint(hostPrefix: "{foo}.data.")
            @http(method: "POST", uri: "/status")
            operation GetStatus {
                input: GetStatusInput,
                output: GetStatusOutput
            }

            structure GetStatusInput {
                @required
                @hostLabel
                foo: String
            }
            
            structure GetStatusOutput {}
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.AwsJson1_1, operations = listOf("GetStatus"))
            .toSmithyModel()

        val ctx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            listOf(),
            HttpTraitResolver(ctx.generationCtx, "application/json")
        )
        generator.render(writer)
        val contents = writer.toString()

        val prefix = "\${input.foo}.data."
        val expectedFragment = """
        val op = SdkHttpOperation.build<GetStatusRequest, GetStatusResponse> {
            serializer = GetStatusOperationSerializer()
            deserializer = GetStatusOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetStatus"
                hostPrefix = "$prefix"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        """
        contents.shouldContainOnlyOnceWithDiff(expectedFragment)
    }
}