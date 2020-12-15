# api-platform-outbound-soap

This service allows other HMRC services to send messages to external SOAP web services.

## `POST /send-message`
Send an IE4N03 SOAP message

This is an endpoint that was created as a PoC and only supports IE4N03 messages.

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | `application/json` |

### Request
```
{
    "message": "<IE4N03>...</IE4N03>"
}
```
| Name | Description |
| --- | --- |
| `message` | The IE4N03 message to send in the SOAP envelope |

### Response
HTTP Status: the HTTP status received by the destination service (CCN2/stub)

### Error scenarios
| Scenario | HTTP Status |
| --- | --- |
| `message` missing from request body | `400` |

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
| `addressing` | Optional property to provide WS addressing data |
| `addressing.from` | This optional property provides the value for the `From` element in the SOAP header |
| `addressing.to` | This optional property provides the value for the `To` element in the SOAP header |
| `addressing.replyTo` | This optional property provides the value for the `ReplyTo` element in the SOAP header |
| `addressing.faultTo` | This optional property provides the value for the `FaultTo` element in the SOAP header |
| `addressing.messageId` | This optional property provides the value for the `MessageID` element in the SOAP header |
| `addressing.relatesTo` | This optional property provides the value for the `RelatesTo` element in the SOAP header |

### Response
HTTP Status: the HTTP status received by the destination service (CCN2/stub)

### Error scenarios
| Scenario | HTTP Status |
| --- | --- |
| `wsdlUrl`, `wsdlOperation` or `messageBody` missing from request body | `400` |
| invalid WSDL | `400` |
| operation not found in the WSDL | `404` |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
