# api-platform-outbound-soap

This service allows other HMRC services to send messages to external SOAP web services. It has a retry mechanism whereby if the
CCN2 SOAP service doesn't return a 2xx response, the request will be retried every 60 seconds for 5 minutes by default.
The total duration and the interval are both configurable.

## `POST /message`
Send a SOAP message for the given operation

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | `application/json` |

### Request
```
{
    "wsdlUrl": "http://example.com/service.wsdl",
    "wsdlOperation": "IE4N03notifyERiskAnalysisHit",
    "messageBody": "<IE4N03 xmlns=\"urn:wco:datamodel:WCO:CIS:1\">...</IE4N03>",
    "confirmationOfDelivery": true,
    "notificationUrl": "http://callmeback.url",
    "addressing": {
        "from": "ICS_NES",
        "to": "ICS_CR",
        "replyTo": "ICS_NES",
        "faultTo": "ICS_NES",
        "messageId": "154517743d31-4251-b820-276809a6762b",
        "relatesTo": "ed74c5da-357f-4412-8b8f-1c5dc6da3013"
    }
}
```
| Name | Description |
| --- | --- |
| `wsdlUrl` | The URL of the WSDL where the operation is defined |
| `wsdlOperation` | The operation to be used in the SOAP envelope |
| `messageBody` | The XML message to send in the SOAP envelope |
| `confirmationOfDelivery` | An optional boolean specifying whether the sender wishes to receive a confirmation of delivery from the target SOAP service. Defaults to false if not provided in the request |
| `notificationUrl` | An optional String property which, if provided, will be used to POST a status update when the message is successfully sent, or is marked as failed after retries have been exhausted. The body will be in the same form as [the response below this table](#response) |
| `addressing` | The property to provide WS addressing data |
| `addressing.from` | This optional property provides the value for the `From` element in the SOAP header. This will default to value in environment specific configuration parameter `addressing.from` if not present in the message. In the absence of configuration this parameter defaults to empty string. If, however, it is present in the message but is an empty string or whitespace then a 400 error will be returned ||
| `addressing.to` | This required property provides the value for the `To` element in the SOAP header |
| `addressing.replyTo` | This optional property provides the value for the `ReplyTo` element in the SOAP header. This will default to value in environment specific configuration parameter `addressing.replyTo` if not present in the message. In the absence of configuration this parameter defaults to empty string. |
| `addressing.faultTo` | This optional property provides the value for the `FaultTo` element in the SOAP header. This will default to value in environment specific configuration parameter `addressing.faultTo` if not present in the message. In the absence of configuration this parameter defaults to empty string. |
| `addressing.messageId` | This required property provides the value for the `MessageID` element in the SOAP header |
| `addressing.relatesTo` | This optional property provides the value for the `RelatesTo` element in the SOAP header |

### Response
HTTP Status: 200 (OK)
```
{
    "globalId": "28a76012-b417-493a-ae64-c241f17a22ca",
    "messageId": "154517743d31-4251-b820-276809a6762b",
    "status": "SENT",
    "ccnHttpStatus": 201
}
```

| Name | Description |
| --- | --- |
| `globalId` | Unique identifier allocated to the request when it is received  |
| `messageId` | The value provided by the `addressing.messageId` property of the request|
| `status` | One of `SENT` if the response from the SOAP service was 2xx, `RETRYING` if an error response was received from the SOAP service, or `FAILED` if all retries have been exhausted|
| `ccnHttpStatus` | The HTTP status code returned by the SOAP service|

### Error scenarios
| Scenario | HTTP Status |
| --- | --- |
| `wsdlUrl`, `wsdlOperation` or `messageBody` missing from request body | `400` |
| `to` or `messageId` missing from addressing property | `400` |
| `from` in addressing property is empty or whitespace| `400` |
| invalid WSDL | `400` |
| operation not found in the WSDL | `404` |

## `POST /acknowledgement`
Allows CCN2 system to asynchronously send an acknowledgment in reply to a message sent to them. Upon receipt of such a message, this service will update the message referred to in 
the RelatesTo field with its new status - either COD or COE - and will append the acknowledgment message, in its entirety, to the 
original request. 

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | `text/xml` |
| `x-soap-action` | `CCN2.Service.Platform.AcknowledgementService/CoD` |

### Confirmation of delivery request
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoD</wsa:Action>
        <wsa:From>
            <wsa:Address>[FROM]</wsa:Address>
        </wsa:From>
        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/ack">[ORIGINAL_MESSAGE_ID]</wsa:RelatesTo>
        <wsa:MessageID>[COD_MESSAGE_ID]</wsa:MessageID>
        <wsa:To>[TO]</wsa:To>
    </soap:Header>
    <soap:Body>
        <ccn2:CoD>
            <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
        </ccn2:CoD>
    </soap:Body>
</soap:Envelope>
```
| Name | Description |
| --- | --- |
| `Action` | Can only be `CCN2.Service.Platform.AcknowledgementService/CoD` |
| `From` | This property identifies the logical address of the sender of the message |
| `RelatesTo` | Contains a `RelationshipType` which will always be `http://ccn2.ec.eu/addressing/ack` and an ID which is the value provided in the addressing.messageId property of the original request |
| `MessageId` | This property uniquely identifies the message within the CCN2 Platform |
| `To` | This property identifies the logical address of the intended receiver of the message |
| `Body.CoD.EventTimestamp` | A timestamp indicating when the error was recorded |

### Response
HTTP Status: 202 (ACCEPTED) with an empty body

### Confirmation of exception request
### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | `text/xml` |
| `x-soap-action` | `CCN2.Service.Platform.AcknowledgementService/CoE` |
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoE</wsa:Action>
        <wsa:From>
            <wsa:Address>[FROM]</wsa:Address>
        </wsa:From>
        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/err">[ORIGINAL_MESSAGE_ID]</wsa:RelatesTo>
        <wsa:MessageID>[COE_MESSAGE_ID]</wsa:MessageID>
        <wsa:To>[TO]</wsa:To>
    </soap:Header>
    <soap:Body>
      <ccn2:CoE>
         <ccn2:Severity>?</ccn2:Severity>
         <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
         <!--Optional:-->
         <ccn2:Payload>
            <soap:Fault>
               <soap:Code>
                  <soap:Value>?</soap:Value>
               </soap:Code>
               <soap:Reason>
                  <soap:Text xml:lang="?">?</soap:Text>
               </soap:Reason>
               <soap:Node>?</soap:Node>
               <soap:Role>?</soap:Role>
               <soap:Detail>
               </soap:Detail>
            </soap:Fault>
            <ccn2:Message>?</ccn2:Message>
         </ccn2:Payload>
      </ccn2:CoE>
   </soap:Body>
</soap:Envelope>
```
| Name | Description |
| --- | --- |
| `Action` | Can only be `CCN2.Service.Platform.AcknowledgementService/CoE` |
| `From` | This property identifies the logical address of the sender of the message |
| `RelatesTo` | Contains a `RelationshipType` which will always be `http://ccn2.ec.eu/addressing/err` and an ID which is the value provided in the addressing.messageId property of the original request |
| `MessageId` | This property uniquely identifies the message within the CCN2 Platform |
| `To` | This property identifies the logical address of the intended receiver of the message |
| `Body.CoE.Severity` | One of Critical, Emergency, Error, Warning, Info in descending order of impact |
| `Body.CoE.EventTimestamp` | A timestamp indicating when the error was recorded |
| `Body.CoE.Payload` | A structure containing a standard SOAP1.2 Fault element |

### Response
HTTP Status: 202 (ACCEPTED) with an empty body

### Error scenarios (both request types)
| Scenario | HTTP Status |
| --- | --- |
| request body cannot be parsed as XML | `400` |
| `RelatesTo` element missing from request body | `400` |
| `RelatesTo` element is blank or contains only whitespace in request body | `400` |
| `x-soap-action` header missing | `400` |
| `x-soap-action` header is blank or contains only whitespace | `400` |
| message ID supplied in `RelatesTo` element in request body does not match that of any message stored in the database | `404` |

## `GET /retrieve/:messageId`
Allows retrieval of the message which has either a `messageId` or a `globalId` matching that in the `id` path parameter

###Response
HTTP Status: 200 (OK) with a body similar to the following:

```
{
    "globalId" : "35a08a4d-fffc-4bb4-9549-6477eaa0aba1",
    "messageId" : "MessageId-A4",
    "soapMessage" : "<IE4N03>payload</IE4N03>",
    "destinationUrl" : "some url",
    "createDateTime" : ISODate("2021-07-06T15:49:43.568Z"),
    "ccnHttpStatus" : 200,
    "coeMessage" : "<soap:Envelope><soap:Header><wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoE</wsa:Action> </soap:Header> <soap:Body> <ccn2:CoE> <ccn2:Severity>?</ccn2:Severity><ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp></ccn2:CoE></soap:Body></soap:Envelope>",
    "status" : "COE"
}
```
### Error scenarios
| Scenario | HTTP Status |
| --- | --- |
| `messageId` path parameter is empty or whitespace | `400` |
| `messageId` path parameter refers to an ID that cannot be found | `404` |

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
