/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.string.shouldMatch
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalStdlibApi::class)
class XmlStreamReaderTest {
    @Test
    fun itDeserializesXml() = runSuspendTest {
        // language=XML
        val actual = """<root><x>1</x><y>2</y></root>""".allTokens()

        actual.shouldContainExactly(
            XmlToken.BeginElement("root"),
            XmlToken.BeginElement("x"),
            XmlToken.Text("1"),
            XmlToken.EndElement("x"),
            XmlToken.BeginElement("y"),
            XmlToken.Text("2"),
            XmlToken.EndElement("y"),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )
    }

    @Test
    fun itHandlesPreamble() = runSuspendTest {
        // language=XML
        val actual = """<?xml version="1.0" encoding="UTF-8" ?><root hello="world"/>""".allTokens()

        actual.shouldContainExactly(
            XmlToken.BeginElement("root", mapOf(XmlToken.QualifiedName("hello") to "world")),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )
    }

    @Test
    fun itDeserializesXmlWithAttributes() = runSuspendTest {
        // language=XML
        val actual =
            """<batch><add id="tt0484562"><field name="title">The Seeker: The Dark Is Rising</field></add><delete id="tt0301199"/></batch>""".allTokens()

        actual.shouldContainExactly(
            XmlToken.BeginElement("batch"),
            XmlToken.BeginElement("add", mapOf(XmlToken.QualifiedName("id") to "tt0484562")),
            XmlToken.BeginElement("field", mapOf(XmlToken.QualifiedName("name") to "title")),
            XmlToken.Text("The Seeker: The Dark Is Rising"),
            XmlToken.EndElement("field"),
            XmlToken.EndElement("add"),
            XmlToken.BeginElement("delete", mapOf(XmlToken.QualifiedName("id") to "tt0301199")),
            XmlToken.EndElement("delete"),
            XmlToken.EndElement("batch"),
            XmlToken.EndDocument
        )
    }

    @Test
    fun garbageInGarbageOut() = runSuspendTest {
        val payload = """you try to parse me once, jokes on me..try twice jokes on you bucko."""
        assertFailsWith(XmlGenerationException::class) { payload.allTokens() }
    }

    @Test
    fun itHandlesNilNodeValues() = runSuspendTest {
        // language=XML
        val actual = """<null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"></null>""".allTokens()
        actual.shouldContainExactly(
            XmlToken.BeginElement("null", mapOf(XmlToken.QualifiedName("nil", "http://www.w3.org/2001/XMLSchema-instance") to "true")),
            XmlToken.EndElement("null"),
            XmlToken.EndDocument
        )
    }

    @Test
    fun itHandlesDefaultNamespace() = runSuspendTest {
        // language=XML
        val actual = """<root xmlns="http://blah"><child/></root>""".allTokens()
        actual.shouldContainExactly(
            XmlToken.BeginElement(XmlToken.QualifiedName("root", "http://blah")),
            XmlToken.BeginElement(XmlToken.QualifiedName("child", "http://blah")),
            XmlToken.EndElement(XmlToken.QualifiedName("child", "http://blah")),
            XmlToken.EndElement(XmlToken.QualifiedName("root", "http://blah")),
            XmlToken.EndDocument
        )
    }

    @Test
    fun itHandlesBasicNamespacePrefixes() = runSuspendTest {
        // language=XML
        val actual = """<root xmlns="http://default" xmlns:pf="http://prefix" pf:attr="some attribute"/>""".allTokens()
        actual.shouldContainExactly(
            XmlToken.BeginElement(
                XmlToken.QualifiedName("root", "http://default"),
                attributes = mapOf(XmlToken.QualifiedName("attr", "http://prefix") to "some attribute")
            ),
            XmlToken.EndElement(XmlToken.QualifiedName("root", "http://default")),
            XmlToken.EndDocument
        )
    }

    @Test
    fun itHandlesNamespaceScopes() = runSuspendTest {
        // language=XML
        val actual = """
            <root xmlns="http://default" xmlns:pf="http://prefix" pf:attr="some attribute">
                <child xmlns:ch="http://child1" ch:child1="whatever">
                    <ch:subchild pf:attr="value"/> 
                </child>
                <child xmlns:ch="http://child2" ch:child2="whatever"/>
            </root>
            """.allTokens()
        actual.shouldContainExactly(
            XmlToken.BeginElement(
                XmlToken.QualifiedName("root", "http://default"),
                attributes = mapOf(XmlToken.QualifiedName("attr", "http://prefix") to "some attribute")
            ),
            XmlToken.BeginElement(
                XmlToken.QualifiedName("child", "http://default"),
                attributes = mapOf(XmlToken.QualifiedName("child1", "http://child1") to "whatever")
            ),
            XmlToken.BeginElement(
                XmlToken.QualifiedName("subchild", "http://child1"),
                attributes = mapOf(XmlToken.QualifiedName("attr", "http://prefix") to "value")
            ),
            XmlToken.EndElement(XmlToken.QualifiedName("subchild", "http://child1")),
            XmlToken.EndElement(XmlToken.QualifiedName("child", "http://default")),
            XmlToken.BeginElement(
                XmlToken.QualifiedName("child", "http://default"),
                attributes = mapOf(XmlToken.QualifiedName("child2", "http://child2") to "whatever")
            ),
            XmlToken.EndElement(XmlToken.QualifiedName("child", "http://default")),
            XmlToken.EndElement(XmlToken.QualifiedName("root", "http://default")),
            XmlToken.EndDocument
        )
    }

    @Test
    fun kitchenSink() = runSuspendTest {
        // language=XML
        val actual = """
        <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <num>1</num>    
          <str>string</str>
          <list>
            <value>1</value>
            <value>2.3456</value>
            <value>3</value>
          </list>
          <nested>
            <l2>
              <list>
                <x>x</x>
                <value>true</value>
              </list>
            </l2>
            <falsey>false</falsey>
          </nested>
          <null xsi:nil="true"></null>
        </root>
        """.allTokens()
        println(actual)
        actual.shouldContainExactly(
            XmlToken.BeginElement("root"),
            XmlToken.BeginElement("num"),
            XmlToken.Text("1"),
            XmlToken.EndElement("num"),
            XmlToken.BeginElement("str"),
            XmlToken.Text("string"),
            XmlToken.EndElement("str"),
            XmlToken.BeginElement("list"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("1"),
            XmlToken.EndElement("value"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("2.3456"),
            XmlToken.EndElement("value"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("3"),
            XmlToken.EndElement("value"),
            XmlToken.EndElement("list"),
            XmlToken.BeginElement("nested"),
            XmlToken.BeginElement("l2"),
            XmlToken.BeginElement("list"),
            XmlToken.BeginElement("x"),
            XmlToken.Text("x"),
            XmlToken.EndElement("x"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("true"),
            XmlToken.EndElement("value"),
            XmlToken.EndElement("list"),
            XmlToken.EndElement("l2"),
            XmlToken.BeginElement("falsey"),
            XmlToken.Text("false"),
            XmlToken.EndElement("falsey"),
            XmlToken.EndElement("nested"),
            XmlToken.BeginElement("null", mapOf(XmlToken.QualifiedName("nil", "http://www.w3.org/2001/XMLSchema-instance") to "true")),
            XmlToken.EndElement("null"),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )
    }

    @Test
    fun itSkipsValuesRecursively() = runSuspendTest {
        // language=XML
        val reader = """
        <payload>
            <x>1></x>
            <unknown>
                <a>a</a>
                <b>b</b>
                <c>
                    <list>
                        <element>d</element>
                        <element>e</element>
                        <element>f</element>
                    </list>
                </c>
                <g>
                    <h>h</h>
                    <i>i</i>
                </g>
            </unknown>
            <y>2></y>
        </payload>
        """.createReader()
        // skip x
        reader.apply {
            nextToken() // begin obj
            nextToken() // x
            nextToken() // value
            nextToken() // end x
        }

        reader.peek().shouldBeInstanceOf<XmlToken.BeginElement> {
            it.id.name shouldMatch "unknown"
        }

        reader.skipNext()

        val y = reader.nextToken() as XmlToken.BeginElement
        y.id.name shouldMatch "y"
    }

    @Test
    fun itSkipsSimpleValues() = runSuspendTest {
        // language=XML
        val reader = """<payload><x>1</x><z>unknown</z><y>2</y></payload>""".createReader()
        // skip x
        reader.apply {
            nextToken() // begin obj
            peek() // x
        }
        reader.skipNext()

        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement> {
            it.id.name shouldMatch "z"
        }

        reader.skipNext()

        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement> {
            it.id.name shouldMatch "y"
        }
    }

    @Test
    fun depthIsHandledCorrectly() = runSuspendTest {
        // language=XML
        val reader = """<root><x>1</x></root>""".createReader()
        reader.currentDepth() shouldBeExactly 0
        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement>() // begin root
        reader.currentDepth() shouldBeExactly 1
        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement>() // begin x
        reader.currentDepth() shouldBeExactly 2
        reader.nextToken().shouldBeInstanceOf<XmlToken.Text>() // text 1
        reader.currentDepth() shouldBeExactly 2
        reader.nextToken().shouldBeInstanceOf<XmlToken.EndElement>() // end x
        reader.currentDepth() shouldBeExactly 2
        reader.nextToken().shouldBeInstanceOf<XmlToken.EndElement>() // end root
        reader.currentDepth() shouldBeExactly 1
        reader.nextToken().shouldBeInstanceOf<XmlToken.EndDocument>() // end document
        reader.currentDepth() shouldBeExactly 0
    }

    @Test
    fun peekDoesNotAffectSkip() = runSuspendTest {
        // language=XML
        val reader = """<root><x>1</x><y>2</y></root>""".createReader()

        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement>()
        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement>()

        reader.peek().shouldBeInstanceOf<XmlToken.Text>()

        reader.skipNext()
        reader.nextToken().shouldBeInstanceOf<XmlToken.BeginElement> {
            it.id.name shouldMatch "y"
        }
    }

    @Test
    fun canHandleComments() = runSuspendTest {
        // language=XML
        val actual = """<root><x>1</x><!-- comment --><y><!-- comment -->2<!-- comment -->4</y></root>""".allTokens()

        actual.shouldContainExactly(
            XmlToken.BeginElement("root"),
            XmlToken.BeginElement("x"),
            XmlToken.Text("1"),
            XmlToken.EndElement("x"),
            XmlToken.BeginElement("y"),
            XmlToken.Text("2"),
            XmlToken.Text("4"),
            XmlToken.EndElement("y"),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )
    }

    @Test
    fun canHandleEscapedCharacters() = runSuspendTest {
        // language=XML
        val actual = """<root><x attr="&lt;&apos;&quot;&amp;&gt;">&lt;&apos;&quot;&amp;&gt;</x></root>""".allTokens()

        actual.shouldContainExactly(
            XmlToken.BeginElement("root"),
            XmlToken.BeginElement("x", mapOf(XmlToken.QualifiedName("attr") to "<'\"&>")),
            XmlToken.Text("<'\"&>"),
            XmlToken.EndElement("x"),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )
    }
}

private fun String.createReader(): XmlStreamReader {
    val payload = this.trimIndent().encodeToByteArray()
    return xmlStreamReader(payload)
}

private inline fun <reified T : XmlToken> XmlToken.shouldBeInstanceOf(block: (T) -> Unit = { }) {
    if (this !is T) {
        throw AssertionError("Expected class ${T::class.simpleName} got $this")
    }
    block(this)
}

private suspend fun String.allTokens(): List<XmlToken> = with(createReader()) {
    val tokens = mutableListOf<XmlToken>()
    while (true) {
        val token = nextToken()
        tokens.add(token)
        if (token is XmlToken.EndDocument) {
            break
        }
    }
    return tokens
}
