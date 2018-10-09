/* SPDX-License-Identifier: Apache-2.0 */
package org.odpi.openmetadata.accessservices.subjectarea.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.odpi.openmetadata.accessservices.subjectarea.ffdc.SubjectAreaErrorCode;
import org.odpi.openmetadata.accessservices.subjectarea.ffdc.exceptions.InvalidParameterException;
import org.odpi.openmetadata.accessservices.subjectarea.ffdc.exceptions.MetadataServerUncontactableException;
import org.odpi.openmetadata.accessservices.subjectarea.responses.SubjectAreaOMASAPIResponse;
import org.odpi.openmetadata.accessservices.subjectarea.responses.VoidResponse;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Arrays;

/**
 * Methods to issue rest calls for the SubjectAreaOMAS using the RestTemplate Spring API
 *
 * TODO there is no security that are added to these rest calls at this time.
 */
public class RestCaller {
    /**
     * Issue a GET REST call that returns a SubjectAreaOMASAPIResponse object.
     * @param className  name of the calling class
     * @param methodName   name of the calling method
     * @param url url
     * @return SubjectAreaOMASAPIResponse  subject area omas response
     * @throws MetadataServerUncontactableException - something went wrong with the REST call stack.
     */
    public static SubjectAreaOMASAPIResponse issueGet(String className,
                                                      String methodName,
                                                      String url) throws MetadataServerUncontactableException {
        return issueExchangeWithoutBody(className, methodName, HttpMethod.GET, url);
    }

    /**
     * Issue a POST REST call that returns a SubjectAreaOMASAPIResponse object.
     *
     * @param className = name of the calling class
     * @param methodName  - name of the calling method
     * @param requestBody - body of the rest request
     * @param url -  the URL for the REST API call
     * @return SubjectAreaOMASAPIResponse    - list of governed asset components
     * @throws MetadataServerUncontactableException - something went wrong with the REST call stack.
     */
    public static SubjectAreaOMASAPIResponse issuePost(String className,
                                                       String methodName,
                                                       String requestBody,
                                                       String url
                                                       )
                                             throws MetadataServerUncontactableException {
        return issueExchangeWithBody(className,methodName,HttpMethod.POST,requestBody,url);

    }
    /**
     * Issue a PUT REST call that returns a SubjectAreaOMASAPIResponse object.
     *
     * @param className = name of the calling class
     * @param methodName  - name of the calling method
     * @param requestBody - body of the rest request
     * @param url -  the URL for the REST API call
     * @return SubjectAreaOMASAPIResponse    - list of governed asset components
     * @throws MetadataServerUncontactableException - something went wrong with the REST call stack.
     */
    public static SubjectAreaOMASAPIResponse issuePut(String className,
                                                       String methodName,
                                                       String requestBody,
                                                       String url
    )
            throws MetadataServerUncontactableException {
        return issueExchangeWithBody(className,methodName,HttpMethod.PUT,requestBody,url);

    }
    /**
     * Issue a DELETE REST call that returns a SubjectAreaOMASAPIResponse object.
     *
     * @param className name of the calling class
     * @param methodName  name of the calling method
     * @param url url for the server
     * @return SubjectAreaOMASAPIResponse    - list of governed asset components
     * @throws MetadataServerUncontactableException - something went wrong with the REST call stack.
     */
    public static SubjectAreaOMASAPIResponse issueDelete(String className,
                                                      String methodName,
                                                      String url
    )
            throws MetadataServerUncontactableException {
        return issueExchangeWithoutBody(className,methodName,HttpMethod.DELETE,url);
    }

    /**
     * Issue a rest exchange call with a rest body.
     * @param className = name of the calling class
     * @param methodName  - name of the calling method
     * @param httpMethod - http method
     * @param requestBody - body of the rest request
     * @param url -  the URL for the REST API call
     * @return SubjectAreaOMASAPIResponse    - list of governed asset components
     * @throws MetadataServerUncontactableException - something went wrong with the REST call stack.
     */
    private static SubjectAreaOMASAPIResponse issueExchangeWithBody(String className,
                                                                    String methodName,
                                                                    HttpMethod httpMethod,
                                                                    String requestBody,
                                                                    String url
    )
            throws MetadataServerUncontactableException {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(new MediaType[] { MediaType.APPLICATION_JSON }));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<Object>(requestBody, headers);

        return issueExchange(className, methodName, httpMethod, url, entity);
    }
    /**
     * Issue a rest exchange call without a rest body.
     * @param className = name of the calling class
     * @param methodName  - name of the calling method
     * @param httpMethod - http method
     * @param url -  the URL for the REST API call
     * @return SubjectAreaOMASAPIResponse    - list of governed asset components
     * @throws MetadataServerUncontactableException - something went wrong with the REST call stack.
     */
    private static SubjectAreaOMASAPIResponse issueExchangeWithoutBody(String className,
                                                                    String methodName,
                                                                    HttpMethod httpMethod,
                                                                    String url
    )
            throws MetadataServerUncontactableException {


        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(new MediaType[] { MediaType.APPLICATION_JSON }));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<Object>( headers);
        return issueExchange(className, methodName, httpMethod, url, entity);
    }

    private static SubjectAreaOMASAPIResponse issueExchange(String className, String methodName, HttpMethod httpMethod, String url, HttpEntity<?> entity) throws MetadataServerUncontactableException {
        SubjectAreaOMASAPIResponse restResponse = null;
        RestTemplate restTemplate = new RestTemplate();
        String resultBody =null;
        ResponseEntity<String> result =null;
        try {
            result = restTemplate.exchange(url, httpMethod, entity, String.class);
            resultBody = result.getBody();
        } catch (Throwable error) {
            SubjectAreaErrorCode errorCode = SubjectAreaErrorCode.CLIENT_SIDE_REST_API_ERROR;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(methodName,
                    url,
                    error.getMessage());

            throw new MetadataServerUncontactableException(errorCode.getHTTPErrorCode(),
                    className,
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction(),
                    error);
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            restResponse =  mapper.readValue(resultBody,SubjectAreaOMASAPIResponse.class);
        } catch (IOException ioException) {
            SubjectAreaErrorCode errorCode = SubjectAreaErrorCode.CLIENT_SIDE_API_REST_RESPONSE_ERROR;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(methodName,
                    url,
                    ioException.getMessage());

            throw new MetadataServerUncontactableException(errorCode.getHTTPErrorCode(),
                    className,
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction(),
                    ioException);
        }
        return restResponse;
    }

    /**
     * Throw a subject area exception indicating that the supplied json could not be parsed.
     * @param className
     * @param methodName
     * @param error
     * @throws InvalidParameterException
     */
    public static void throwJsonParseError (String className, String methodName,  JsonProcessingException error) throws InvalidParameterException {
        SubjectAreaErrorCode errorCode = SubjectAreaErrorCode.UNABLE_TO_PARSE_SUPPLIED_JSON;
        String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(methodName,
                error.getMessage());

        throw new InvalidParameterException(errorCode.getHTTPErrorCode(),
                className,
                methodName,
                errorMessage,
                errorCode.getSystemAction(),
                errorCode.getUserAction(),
                error);
    }
}