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
| `addressing` | Optional property to provide WS addressing data |
| `addressing.from` | This optional property provides the value for the `From` element in the SOAP header |
| `addressing.to` | This optional property provides the value for the `To` element in the SOAP header |
| `addressing.replyTo` | This optional property provides the value for the `ReplyTo` element in the SOAP header |
| `addressing.faultTo` | This optional property provides the value for the `FaultTo` element in the SOAP header |
| `addressing.messageId` | This optional property provides the value for the `MessageID` element in the SOAP header |
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
| `messageId` | This optional property, if present, is the value provided by the `addressing.messageId` property of the request|
| `status` | One of `SENT` if the response from the SOAP service was 2xx, `RETRYING` if an error response was received from the SOAP service, or `FAILED` if all retries have been exhausted|
| `ccnHttpStatus` | The HTTP status code returned by the SOAP service|

### Error scenarios
| Scenario | HTTP Status |
| --- | --- |
| `wsdlUrl`, `wsdlOperation` or `messageBody` missing from request body | `400` |
| invalid WSDL | `400` |
| operation not found in the WSDL | `404` |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
