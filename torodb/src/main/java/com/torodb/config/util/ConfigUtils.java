/*
 *     This file is part of ToroDB.
 *
 *     ToroDB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ToroDB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with ToroDB. If not, see <http://www.gnu.org/licenses/>.
 *
 *     Copyright (c) 2014, 8Kdata Technology
 *     
 */

package com.torodb.config.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.github.fge.jsonschema.SchemaVersion;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ListProcessingReport;
import com.github.fge.jsonschema.core.report.ListReportProvider;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import com.torodb.CliConfig;
import com.torodb.config.model.Config;

public class ConfigUtils {

	public static Config readConfig(final CliConfig cliConfig) throws FileNotFoundException, JsonProcessingException,
			IOException, JsonParseException, JsonMappingException, Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(Include.NON_NULL);
		
		JsonNode configNode = objectMapper.valueToTree(new Config());
		
		if (cliConfig.hasConfFile() || cliConfig.hasXmlConfFile()) {
			ObjectMapper mapper = null;
			InputStream inputStream = null;
			if (cliConfig.hasConfFile()) {
				mapper = new YAMLMapper();
				inputStream = cliConfig.getConfInputStream();
			} else if (cliConfig.hasXmlConfFile()) {
				mapper = new XmlMapper();
				inputStream = cliConfig.getXmlConfInputStream();
			}

			if (inputStream != null) {
				configNode = mapper.readTree(inputStream);
			}
		}

		if (cliConfig.getParams() != null) {
			YAMLMapper yamlMapper = new YAMLMapper();
			yamlMapper.setSerializationInclusion(Include.NON_NULL);
			for (String paramPathValue : cliConfig.getParams()) {
				int paramPathValueSeparatorIndex = paramPathValue.indexOf('=');
				String pathAndProp = paramPathValue.substring(0, paramPathValueSeparatorIndex);

				if (pathAndProp.startsWith("/")) {
					pathAndProp = pathAndProp.substring(1);
				}

				pathAndProp = "/" + pathAndProp;

				String value = paramPathValue.substring(paramPathValueSeparatorIndex + 1);

				mergeParam(yamlMapper, configNode, pathAndProp, value);
			}
		}
		
		configNode = mergeWithDefaults(configNode);
		
		validateWithJackson(configNode);

		Config config = objectMapper.treeToValue(configNode, Config.class);

		validateBean(config);

		return config;
	}

	private static void mergeParam(ObjectMapper objectMapper, JsonNode configRootNode, String pathAndProp, String value)
			throws Exception {
		String path = pathAndProp.substring(0, pathAndProp.lastIndexOf("/"));
		String prop = pathAndProp.substring(pathAndProp.lastIndexOf("/") + 1);

		JsonPointer pathPointer = JsonPointer.compile(path);
		JsonNode pathNode = configRootNode.at(pathPointer);

		if (pathNode.isMissingNode() || pathNode.isNull()) {
			JsonPointer currentPointer = pathPointer;
			JsonPointer childOfCurrentPointer = null;
			List<JsonPointer> missingPointers = new ArrayList<JsonPointer>();
			List<JsonPointer> childOfMissingPointers = new ArrayList<JsonPointer>();
			do {
				if (pathNode.isMissingNode() || pathNode.isNull()) {
					missingPointers.add(0, currentPointer);
					childOfMissingPointers.add(0, childOfCurrentPointer);
				}

				childOfCurrentPointer = currentPointer;
				currentPointer = currentPointer.head();
				pathNode = configRootNode.at(currentPointer);
			} while (pathNode.isMissingNode() || pathNode.isNull());

			for (int missingPointerIndex = 0; missingPointerIndex < missingPointers.size(); missingPointerIndex++) {
				final JsonPointer missingPointer = missingPointers.get(missingPointerIndex);
				final JsonPointer childOfMissingPointer = childOfMissingPointers.get(missingPointerIndex);

				final List<JsonNode> newNodes = new ArrayList<JsonNode>();

				if (pathNode.isObject()) {
					((ObjectNode) pathNode).set(missingPointer.last().getMatchingProperty(),
							createNode(childOfMissingPointer, newNodes));
				} else if (pathNode.isArray() && missingPointer.last().mayMatchElement()) {
					for (int index = ((ArrayNode) pathNode).size(); index < missingPointer.last().getMatchingIndex()
							+ 1; index++) {
						((ArrayNode) pathNode).add(createNode(childOfMissingPointer, newNodes));
					}
				} else {
					throw new RuntimeException("Cannot set param " + pathAndProp + "=" + value);
				}

				pathNode = newNodes.get(newNodes.size() - 1);
			}
		}

		ObjectNode objectNode = (ObjectNode) pathNode;
		Object valueAsObject = objectMapper.readValue(value, Object.class);
		if (valueAsObject != null) {
			JsonNode valueNode = objectMapper.valueToTree(valueAsObject);
			objectNode.set(prop, valueNode);
		} else {
			objectNode.remove(prop);
		}
	}

	private static JsonNode createNode(JsonPointer childOfPointer, List<JsonNode> newNodes) {
		JsonNode newNode = null;

		if (childOfPointer == null || !childOfPointer.last().mayMatchElement()) {
			newNode = JsonNodeFactory.instance.objectNode();
		} else {
			newNode = JsonNodeFactory.instance.arrayNode();
		}

		newNodes.add(newNode);

		return newNode;
	}

	public static void printYamlConfig(Config config, PrintStream printStream)
			throws IOException, JsonGenerationException, JsonMappingException {
		ObjectMapper objectMapper = new YAMLMapper();
		objectMapper.configure(Feature.ALLOW_COMMENTS, true);
		objectMapper.configure(Feature.ALLOW_YAML_COMMENTS, true);
		objectMapper.writeValue(printStream, config);
	}

	public static void printXmlConfig(Config config, PrintStream printStream)
			throws IOException, JsonGenerationException, JsonMappingException {
		ObjectMapper objectMapper = new XmlMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		objectMapper.configure(Feature.ALLOW_COMMENTS, true);
		objectMapper.configure(Feature.ALLOW_YAML_COMMENTS, true);
		ObjectWriter objectWriter = objectMapper.writer();
		objectWriter = objectWriter.withRootName("config");
		objectWriter.writeValue(printStream, config);
	}

	public static void printParamDescriptionFromConfigSchema(PrintStream printStream, int tabs)
			throws UnsupportedEncodingException, JsonMappingException {
		ObjectMapper objectMapper = new ObjectMapper();
		ResourceBundle resourceBundle = PropertyResourceBundle.getBundle("ConfigMessages");
		DescriptionFactoryWrapper visitor = new DescriptionFactoryWrapper(resourceBundle, printStream, tabs);
		objectMapper.acceptJsonFormatVisitor(objectMapper.constructType(Config.class), visitor);
	}

	private static JsonNode mergeWithDefaults(JsonNode configNode) {
		if (configNode.isObject()) {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.setSerializationInclusion(Include.NON_NULL);
			Config config = new Config();
			
			//NOTE: Some defaults can not retrieved for subtypes so we try to read the configuration
			// and generate those defaults before validation
			try {
				config = objectMapper.treeToValue(configNode, Config.class);
			} catch(JsonProcessingException jsonProcessingException) {
				
			} catch(IllegalArgumentException illegalArgumentException) {
				
			}
			
			ObjectNode mergedConfigNode = (ObjectNode) objectMapper.valueToTree(config);
			merge(mergedConfigNode, (ObjectNode) configNode);
			configNode = mergedConfigNode;
		}
		
		return configNode;
	}
	
	private static void merge(ObjectNode primary, ObjectNode backup) {
		Iterator<String> fieldNames = backup.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			JsonNode primaryValue = primary.get(fieldName);
			JsonNode backupValue = backup.get(fieldName);
			
			if (backupValue.isValueNode() || primaryValue == null ||
					(backupValue.isObject() && !primaryValue.isObject()) ||
					(backupValue.isArray()) && !primaryValue.isArray()) {
				primary.set(fieldName, backupValue.deepCopy());
			} else if (backupValue.isObject()) {
				merge((ObjectNode) primaryValue, (ObjectNode) backupValue);
			} else if (backupValue.isArray()) {
				merge((ArrayNode) primaryValue, (ArrayNode) backupValue); 
			}
		}
	}

	private static void merge(ArrayNode primary, ArrayNode backup) {
		for (int index = 0; index < backup.size(); index++) {
			if (index >= primary.size()) {
				primary.add(backup.get(index));
			} else {
				JsonNode primaryValue = primary.get(index);
				JsonNode backupValue = backup.get(index);
				
				if (backupValue.isValueNode() || 
						(backupValue.isObject() && !primaryValue.isObject()) ||
						(backupValue.isArray()) && !primaryValue.isArray()) {
					primary.set(index, backupValue.deepCopy());
				} else if (backupValue.isObject()) {
					merge((ObjectNode) primaryValue, (ObjectNode) backupValue);
				} else if (backupValue.isArray()) {
					merge((ArrayNode) primaryValue, (ArrayNode) backupValue); 
				}
			}
		}
	}

	private static void validateWithJackson(JsonNode configNode) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		validateJsonSchema(objectMapper, configNode);
	}

	private static void validateJsonSchema(ObjectMapper objectMapper, JsonNode configNode) throws JsonMappingException {
		CustomSchemaFactoryWrapper visitor = new CustomSchemaFactoryWrapper();
		visitor.setJsonSchemaFactory(new CustomJsonSchemaFactory());
		objectMapper.acceptJsonFormatVisitor(objectMapper.constructType(Config.class), visitor);
		JsonSchema jsonSchema = visitor.finalSchema();
		JsonNode schemaNode = objectMapper.valueToTree(jsonSchema);
		JsonSchemaFactory factory = JsonSchemaFactory.newBuilder()
				.setValidationConfiguration(
						ValidationConfiguration.newBuilder().setDefaultVersion(SchemaVersion.DRAFTV3).freeze())
				.setReportProvider(new ListReportProvider(LogLevel.DEBUG, LogLevel.NONE)).freeze();
		JsonValidator validator = factory.getValidator();
		ProcessingReport processingReport = null;
		try {
			processingReport = validator.validate(schemaNode, configNode);
		} catch (ProcessingException processingException) {
			ListProcessingReport listProcessingReport = new ListProcessingReport();
			listProcessingReport.log(LogLevel.DEBUG, processingException.getProcessingMessage());
			processingReport = listProcessingReport;
		}

		if (!processingReport.isSuccess()) {
			StringBuilder constraintViolationExceptionMessageBuilder = new StringBuilder();
			for (ProcessingMessage processingMessage : processingReport) {
				JsonNode messageNode = processingMessage.asJson();
				String message = messageNode.has("message") ? messageNode.get("message").textValue() : "unknown";
				String pointer = messageNode.has("instance") && messageNode.get("instance").isObject()
						&& ((ObjectNode) messageNode.get("instance")).has("pointer")
								? ((ObjectNode) messageNode.get("instance")).get("pointer").textValue() : "?";
				if (constraintViolationExceptionMessageBuilder.length() > 0) {
					constraintViolationExceptionMessageBuilder.append(", ");
				}
				constraintViolationExceptionMessageBuilder.append(pointer);
				constraintViolationExceptionMessageBuilder.append(": ");
				constraintViolationExceptionMessageBuilder.append(message);
			}
			throw new IllegalArgumentException(constraintViolationExceptionMessageBuilder.toString());
		}
	}

	public static void validateBean(Config config) {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Config>> constraintViolations = validator.validate(config);
		if (!constraintViolations.isEmpty()) {
			StringBuilder constraintViolationExceptionMessageBuilder = new StringBuilder();
			for (ConstraintViolation<Config> constraintViolation : constraintViolations) {
				if (constraintViolationExceptionMessageBuilder.length() > 0) {
					constraintViolationExceptionMessageBuilder.append(", ");
				}
				Path path = constraintViolation.getPropertyPath();
				JsonPointer pointer = toJsonPointer(path);
				constraintViolationExceptionMessageBuilder.append(pointer.toString());
				constraintViolationExceptionMessageBuilder.append(": ");
				constraintViolationExceptionMessageBuilder.append(constraintViolation.getMessage());
			}
			throw new IllegalArgumentException(constraintViolationExceptionMessageBuilder.toString());
		}
	}

	public static JsonPointer toJsonPointer(Path path) {
		JsonPointer pointer = JsonPointer.valueOf(null);
		for (Path.Node pathNode : path) {
			if (pathNode.getIndex() != null) {
				pointer = pointer.append(JsonPointer.valueOf("/" + pathNode.getIndex()));
			} else if (pathNode.getName() != null) {
				pointer = pointer.append(JsonPointer.valueOf("/" + pathNode.getName()));
			}
		}
		return pointer;
	}

}
