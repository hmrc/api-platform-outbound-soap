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
    "wsdlUrl": "http://example.com/service.wsdl"
    "wsdlOperation": "IE4N03notifyERiskAnalysisHit"
    "messageBody": "<IE4N03>...</IE4N03>"
}
```
| Name | Description |
| --- | --- |
| `wsdlUrl` | The URL of the WSDL where the operation is defined |
| `wsdlOperation` | The operation to be used in the SOAP envelope |
| `messageBody` | The XML message to send in the SOAP envelope |

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
