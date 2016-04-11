/*
 * Copyright 2016 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.entityservices;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mortbay.log.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hp.hpl.jena.sparql.algebra.Transformer;
import com.hp.hpl.jena.sparql.function.library.e;
import com.marklogic.client.document.DocumentWriteSet;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.eval.EvalResult;
import com.marklogic.client.eval.EvalResultIterator;
import com.marklogic.client.io.DOMHandle;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.FileHandle;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.StringHandle;
//import com.marklogic.entityservices.tests.TestEvalException;
import com.sun.org.apache.xerces.internal.parsers.XMLParser;

/**
 * Tests server function es:conversion-module-generate
 * 
 * Covered so far: validity of XQuery module generation
 * 
 * extract-instance-Order
 * 
 * The default extraction model is valid, and each function runs as though
 * the source for an entity is the same as its model.  That is, 
 * if you extract an instance using extract-instance-Order() the original
 * generated function expects an input that corresponds exactly to the persisted
 * output of an Order.
 */
public class TestEsConversionModuleGenerator extends EntityServicesTestBase {

	private StringHandle xqueryModule = new StringHandle();
	private static TextDocumentManager docMgr;
	private static Map<String, StringHandle> conversionModules;
	
	@BeforeClass
	public static void setupClass() {
		setupClients();
		// save xquery module to modules database
		docMgr = modulesClient.newTextDocumentManager();	
		
		conversionModules = generateConversionModules();
		storeConversionModules(conversionModules);
		storeCustomConversionModules();

	}
	
	private static void storeConversionModules(Map<String, StringHandle> moduleMap) {
		
		DocumentWriteSet writeSet = docMgr.newWriteSet();
				
		for (String entityTypeName : moduleMap.keySet()) {
			
			String moduleName = "/conv/" + entityTypeName.replaceAll("\\.(xml|json)", ".xqy");
			writeSet.add(moduleName, moduleMap.get(entityTypeName));
		}
		docMgr.write(writeSet);
	}
	
	private static void storeCustomConversionModules() {
		
		DocumentWriteSet writeSet = docMgr.newWriteSet();
		Collection<File> custConvMod = TestSetup.getInstance().getTestResources("/customized-conversion-module");
		
		for (File f : custConvMod) {
		
			String moduleName = "/conv/" + f.getName();
			DocumentMetadataHandle metadata = new DocumentMetadataHandle();
	        
	    	writeSet.add(moduleName, metadata, new FileHandle(f));
		}
		docMgr.write(writeSet);
	}
	
	private static Map<String, StringHandle> generateConversionModules() {
		Map<String, StringHandle> map = new HashMap<String, StringHandle>();
		
		for (String entityType : entityTypes) {
			if (entityType.contains(".xml")||entityType.contains(".jpg")||entityType.contains("invalid-")) {continue; };
			
			logger.info("Generating conversion module: " + entityType);
			StringHandle xqueryModule = new StringHandle();
			try {
				xqueryModule = evalOneResult("es:conversion-module-generate( es:entity-type-from-node( fn:doc( '"+entityType+"')))", xqueryModule);
			} catch (com.marklogic.entityservices.TestEvalException e) {
				throw new RuntimeException(e);
			}
			map.put(entityType, xqueryModule);
		}
		return map;
	}

	
	public String[] storeConversionModuleAsXqy(String entityTypeName) throws IOException, TestEvalException {
		
		String arr[] = new String[2];
		String line;
		xqueryModule = evalOneResult("es:conversion-module-generate( es:entity-type-from-node( fn:doc( '"+entityTypeName+"')))", xqueryModule);
	
		// save xquery module to modules database
		TextDocumentManager docMgr = modulesClient.newTextDocumentManager();
		String moduleName = "/conv/" + entityTypeName.replaceAll("\\.(xml|json)", ".xqy");
		docMgr.write(moduleName, xqueryModule);
		
		//Below code is to get docTitle and namespace in the generated xqy  module
		BufferedReader bf = new BufferedReader(new StringReader(xqueryModule.get()));
		
		while((line=bf.readLine()) != null){
		    if(line.startsWith("module namespace ")) {
		        arr = line.substring(17).split(" = ");
		        int len = arr[1].length();
		        arr[1] = arr[1].substring(1, len-2);
		        //logger.info("\n" + arr[0] + "\n" + arr[1]);
		        break;
		    } else {
		        continue;
		    }
		}
		
		return arr;
		
	}
	
	public String getDocTitle(String entityTypeName) throws IOException, TestEvalException {
		
		String docTitle = null;		
		docTitle = this.storeConversionModuleAsXqy(entityTypeName)[0];
		return docTitle;
		
	}
	
	public String getNameSpace(String entityTypeName) throws IOException, TestEvalException {
		
		String ns = null;		
		ns = this.storeConversionModuleAsXqy(entityTypeName)[1];
		return ns;
		
	}
	
	/*
	 * private StringHandle getEntityTypes(String entityTypeName) throws TestEvalException, IOException {
	 
		
		String docTitle = getDocTitle(entityTypeName);
		EvalResultIterator results =  eval("map:keys(map:get(es:entity-type-from-node( doc('"+entityTypeName+"') ), \"definitions\"))");
		EvalResult result = null;

		while (results.hasNext()) {
			result = results.next();
			return result.get(new StringHandle()));
		}		
	}
	*/
	
	private boolean getConversionValidationResult(String entityDoc, String function) throws TestEvalException {
		
		boolean status = false;
	 	try {
	 		status = xqueryModule.get().contains(function);
	 		assertTrue(function + " not generated for entity document: " + entityDoc, status);
	 	} catch (Exception e) {
   			logger.info("\nGot exception:\n" + e.getMessage());  
	 	}
	 	
	 	return status;		
	}
	
	private String getKeys(String path, String entityType) throws TransformerException, SAXException, IOException {
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream(path + entityType);
		Document controlDoc = builder.parse(is);
				
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		
		return writer.toString();
	}
	
	@Test
	//This test verifies that conversion module generates 6+n functions, where is number of entity type names
	public void testConversionModuleGenerate() throws TestEvalException, IOException {
		
		String entityType = "valid-ref-same-document.xml";
		String docTitle = getDocTitle(entityType);		
		
		//Validating generated extract instances of entity type names.
		EvalResultIterator results =  eval("map:keys(map:get(es:entity-type-from-node( doc('"+entityType+"') ), \"definitions\"))");
		EvalResult result = null;

		while (results.hasNext()) {
			result = results.next();
			getConversionValidationResult(entityType, docTitle + ":extract-instance-" + result.get(new StringHandle()));
		}
	
		//Validating generated functions. TODO uncomment the verification for source document
		getConversionValidationResult(entityType, docTitle+":instance-to-canonical-xml");
		getConversionValidationResult(entityType, docTitle+":instance-to-envelope");
		getConversionValidationResult(entityType, docTitle+":instance-from-document");
		getConversionValidationResult(entityType, docTitle+":instance-xml-from-document");
		getConversionValidationResult(entityType, docTitle+":instance-json-from-document");
		//getConversionValidationResult(initialTest, docTitle+":sources-from-document");
			
	}
	
	@Test
	//This test verifies that conversion module generates 6 functions + 0 entity type names extract
	public void testConvModGenForZeroEntityTypes() throws IOException, TestEvalException {
		
		String entityType = "valid-definitions-empty.json";
		String docTitle = getDocTitle(entityType);		
		boolean test = false;
		
		//Validating generated extract instances of entity type names. Should be 0
		EvalResultIterator results =  eval("map:keys(map:get(es:entity-type-from-node( doc('"+entityType+"') ), \"definitions\"))");
		EvalResult result = null;

		while (results.hasNext()) {
			result = results.next();
			test = getConversionValidationResult(entityType, docTitle + ":extract-instance-" + result.get(new StringHandle()));
		}
		if(test==true){
			fail("extract-instance should not be generated for this test. Check the input file: "+entityType);
		}
	
		//Validating generated functions. TODO uncomment the verification for source document
		getConversionValidationResult(entityType, docTitle+":instance-to-canonical-xml");
		getConversionValidationResult(entityType, docTitle+":instance-to-envelope");
		getConversionValidationResult(entityType, docTitle+":instance-from-document");
		getConversionValidationResult(entityType, docTitle+":instance-xml-from-document");
		getConversionValidationResult(entityType, docTitle+":instance-json-from-document");
		//getConversionValidationResult(initialTest, docTitle+":sources-from-document");
			
	}
	
	@Test
	//This test verifies that conversion module does not throw an error when an invalid ET( missing info section ) is input
	public void testConvModGenForInvalidET() throws TestEvalException, IOException {
		
		String entityType = "valid-missing-info.json";
		String[] res = null;
		try {
			res = storeConversionModuleAsXqy(entityType);
		if(res[0]!=null) {
			fail("Testing for conversion module with missing info section. Check the input ET doc file: "+entityType);
		} 
		} catch (Exception e) {
			logger.info("Got exception: "+e.getMessage());
		}
	
	}
	
	@Test
	public void testExtractInstanceOrder() throws IOException, TestEvalException, SAXException, TransformerException {
		/*
		 * Might need to edit the gen module to get expected output
		 */
		String entityType = "valid-ref-combo-sameDocument-subIri.json";
		String sourceDocument = "10248.xml";
		String ns = getNameSpace(entityType);
		
		JacksonHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
		              "ext:extract-instance-Order( doc('"+sourceDocument+"') )", new JacksonHandle());
		
		JsonNode extractInstanceResult = handle.get();
		logger.info("This is the extracted instance: \n" + extractInstanceResult);
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = this.getClass().getResourceAsStream("/test-extract-instance/instance-Order.json");

		JsonNode control = mapper.readValue(is, JsonNode.class);

		org.hamcrest.MatcherAssert.assertThat(control, org.hamcrest.Matchers.equalTo(extractInstanceResult));
		
	}
	

	@Test
	public void testExtractInstanceCustomer() throws IOException, TestEvalException {
		
		String entityType = "valid-ref-combo-sameDocument-subIri.json";
		String sourceDocument = "VINET.xml";
		String ns = getNameSpace(entityType);
		
		JacksonHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
		              "ext:extract-instance-Customer( doc('"+sourceDocument+"') )", new JacksonHandle());
		
		JsonNode extractInstanceResult = handle.get();
		logger.info("This is the extracted instance: \n" + extractInstanceResult);
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = this.getClass().getResourceAsStream("/test-extract-instance/instance-Customer.json");

		JsonNode control = mapper.readValue(is, JsonNode.class);

		org.hamcrest.MatcherAssert.assertThat(control, org.hamcrest.Matchers.equalTo(extractInstanceResult));
		
	}

	@Test
	public void testExtractInstanceProduct() throws IOException, TestEvalException {
		/*
		 * Might need to edit the gen module to get expected output
		 */
		String entityType = "valid-ref-combo-sameDocument-subIri.json";
		String sourceDocument = "11.xml";
		String ns = getNameSpace(entityType);
		
		JacksonHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
		              "ext:extract-instance-Product( doc('"+sourceDocument+"') )", new JacksonHandle());
		
		JsonNode extractInstanceResult = handle.get();
		logger.info("This is the extracted instance: \n" + extractInstanceResult);
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = this.getClass().getResourceAsStream("/test-extract-instance/instance-Product.json");

		JsonNode control = mapper.readValue(is, JsonNode.class);

		org.hamcrest.MatcherAssert.assertThat(control, org.hamcrest.Matchers.equalTo(extractInstanceResult));
		
	}
	
	@Test
	public void testExtractInstanceInvalid() throws IOException, TestEvalException {
		
		String entityType = "valid-ref-combo-sameDocument-subIri.json";
		String sourceDocument = "11.xml";
		String ns = getNameSpace(entityType);
		
		JacksonHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
		              "ext:extract-instance-Customer( doc('"+sourceDocument+"') )", new JacksonHandle());
		
		JsonNode extractInstanceResult = handle.get();
		logger.info("This is the extracted instance: \n" + extractInstanceResult);
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = this.getClass().getResourceAsStream("/test-extract-instance/instance-Invalid.json");

		JsonNode control = mapper.readValue(is, JsonNode.class);

		org.hamcrest.MatcherAssert.assertThat(control, org.hamcrest.Matchers.equalTo(extractInstanceResult));
		
	}

	@Test
	public void testExtInstWithNoArgs() throws IOException, TestEvalException {
		
		String entityType = "valid-ref-combo-sameDocument-subIri.xml";
		String ns = getNameSpace(entityType);
		try{
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
		              "ext:extract-instance-Order()", new StringHandle());
		
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Too few args, expected 1 but got 0"));
		}
		
	}
	
	@Test
	public void testExtInstWithTooManyArgs() throws IOException, TestEvalException {
		
		String entityType = "valid-ref-combo-sameDocument-subIri.xml";
		String source1 = "10248.xml";
		String source2 = "ANTON.xml";
		String ns = getNameSpace(entityType);

		try {
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
		              "ext:extract-instance-Order( doc('"+source1+"'),doc('"+source2+"'))", new StringHandle());
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Too many args, expected 1 but got 2"));
		}
	}
	
	@Test
	public void testInstanceToCanonicalXml() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-combo-sameDocument-subIri.xml";
		String sourceDocument = "10248.xml";
		String ns = getNameSpace(entityType);
		
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-to-canonical-xml(ext:extract-instance-Order( doc('"+sourceDocument+"') ))", new StringHandle());
		
		String actualDoc = handle.get();              
		
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-canonical/" + entityType);
		Document controlDoc = builder.parse(is);
				
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString());
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);	
	}
	
	@Test
	public void testInstanceToCanonicalXml2() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "ALFKI.xml";
		String ns = getNameSpace(entityType);
		
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-to-canonical-xml(ext:extract-instance-Customer( doc('"+sourceDocument+"') ))", new StringHandle());
		
		String actualDoc = handle.get();              
		
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-canonical/" + entityType);
		Document controlDoc = builder.parse(is);
				
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);	
	}
	
	@Test
	public void testInstanceToEnvelope() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "ALFKI.xml";
		String ns = getNameSpace(entityType);
		
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-to-envelope(ext:extract-instance-Customer( doc('"+sourceDocument+"') ))", new StringHandle());
		
		String actualDoc = handle.get();              
		
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-envelope/" + entityType);
		Document controlDoc = builder.parse(is);
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);	
	}
	
	@Test
	public void testInstanceToEnvelope2() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "10249.xml";
		String ns = getNameSpace(entityType);
		
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-to-envelope(ext:extract-instance-Order( doc('"+sourceDocument+"') ))", new StringHandle());
		
		String actualDoc = handle.get();              
		
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-envelope/" + "valid-ref-same-document-2.xml");
		Document controlDoc = builder.parse(is);
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);	
	}
	
	@Test
	public void testInstanceGetAttachments() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "ALFKI.xml";
		String ns = getNameSpace(entityType);
		String envelopeDoc = "valid-ref-combo-sameDocument-subIri-envelope.xml";
		
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "xdmp:document-insert('valid-ref-combo-sameDocument-subIri-envelope.xml', ext:instance-to-envelope(ext:extract-instance-Customer( doc('"+sourceDocument+"') )))", new StringHandle());
		StringHandle handle2 = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-get-attachments(doc('"+envelopeDoc+"'))", new StringHandle());
		String actualDoc = handle2.get();
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-attachments/" + entityType);
		Document controlDoc = builder.parse(is);
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);	
	}
	
	@Test
	public void testInstanceFromDocumentNoRef() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "VINET.xml";
		String ns = getNameSpace(entityType);
		
		JacksonHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-from-document(ext:instance-to-envelope(ext:extract-instance-Customer( doc('"+sourceDocument+"'))))", new JacksonHandle());
		JsonNode actualDoc = handle.get();
		//Get the keys file as input stream
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = this.getClass().getResourceAsStream("/test-instance-from-document/noRef-from-document.json");

		JsonNode control = mapper.readValue(is, JsonNode.class);

		org.hamcrest.MatcherAssert.assertThat(control, org.hamcrest.Matchers.equalTo(actualDoc));	
	}
	
	@Test
	public void testInstanceXmlFromDocumentNoRef() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "VINET.xml";
		String ns = getNameSpace(entityType);
		
		StringHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-xml-from-document(ext:instance-to-envelope(ext:extract-instance-Customer( doc('"+sourceDocument+"'))))", new StringHandle());
		String actualDoc = handle.get();
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-instance-from-document/noRef-xml-from-document.xml");
		Document controlDoc = builder.parse(is);
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);
	}
	
	@Test
	public void testInstanceXmlFromDocumentRefSame() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String sourceDocument = "10248.xml";
		
		StringHandle handle = evalOneResult("import module namespace gen = 'http://refSameDocument#Northwind-Ref-Same-Document-0.0.1' at '/conv/valid-ref-same-doc-gen.xqy'; "+
	              "gen:instance-xml-from-document(gen:instance-to-envelope(gen:extract-instance-Order( doc('"+sourceDocument+"'))))", new StringHandle());
		String actualDoc = handle.get();
		//Get the keys file as controlDoc
		InputStream is = this.getClass().getResourceAsStream("/test-instance-from-document/refSame-xml-from-document.xml");
		Document controlDoc = builder.parse(is);
		// convert DOM Document into a string
		StringWriter writer = new StringWriter();
		DOMSource domSource = new DOMSource(controlDoc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		javax.xml.transform.Transformer transformer = null;
		try {
	    		transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformer.transform(domSource, result);
				
		//logger.info("XML IN String format is: \n" + writer.toString()); 
		//logger.info("actualDoc now ::::" + actualDoc);
		XMLUnit.setIgnoreWhitespace(true);
		XMLAssert.assertXMLEqual(writer.toString(), actualDoc);
	}
	
	/*
	 * TODO 1
	 * Add tests for RefCombo for all 3 instance-from-document APIs after getting more info for bug 38883
	 * 
	 * TODO 2
	 * Add tests for RefSame for instance-json-from-document and instance-from-document after bug 39018 is fixed
	 * 
	 */
	
	@Test
	public void testInstanceJsonFromDocumentNoRef() throws IOException, TestEvalException, SAXException, TransformerException {
		
		String entityType = "valid-ref-same-document.xml";
		String sourceDocument = "VINET.xml";
		String ns = getNameSpace(entityType);
		
		JacksonHandle handle = evalOneResult("import module namespace ext = \""+ns+"\" at \"/conv/"+entityType.replaceAll("\\.(xml|json)", ".xqy")+"\"; "+
	              "ext:instance-json-from-document(ext:instance-to-envelope(ext:extract-instance-Customer( doc('"+sourceDocument+"'))))", new JacksonHandle());
		JsonNode actualDoc = handle.get();
		//Get the keys file as input stream
		ObjectMapper mapper = new ObjectMapper();
		InputStream is = this.getClass().getResourceAsStream("/test-instance-from-document/noRef-json-from-document.json");

		JsonNode control = mapper.readValue(is, JsonNode.class);

		org.hamcrest.MatcherAssert.assertThat(control, org.hamcrest.Matchers.equalTo(actualDoc));	
	}
}