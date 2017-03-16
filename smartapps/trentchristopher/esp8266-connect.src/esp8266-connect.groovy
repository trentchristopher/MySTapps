/**
 *  ESP8266 Connect
 *
 *  Copyright 2016 Trent Christopher
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "ESP8266 (Connect)",
    namespace: "trentchristopher",
    author: "Trent Christopher",
    description: "Used to connect an ESP8266 to SmartThings",
    category: "",
    iconUrl: "https://www.raspberrypi.org/wp-content/uploads/2015/08/raspberry-pi-logo.png",
    iconX2Url: "https://www.raspberrypi.org/wp-content/uploads/2015/08/raspberry-pi-logo.png",
    singleInstance: true
)

preferences {
    page(name:"selectESP", title:"Serching for devices...", content:"selectESP")
}

def searchTarget(){ return "urn:schemas-upnp-org:device:Basic:1" }

def selectESP() {
    def refreshInterval = 3

	/* Subscribe to responses from this device */
    ssdpSubscribe()

    /* Send M-Search command to this device */
    ssdpDiscover()

    def devicesForDialog = getDevicesForDialog()

    return dynamicPage(name:"selectESP", title:"", nextPage:"", refreshInterval: refreshInterval, install:true, uninstall: true) {
        section("") {
        	image "http://media.point2.com/p2a/module/6e99/6311/70bc/e95acda9115d52af1106/original.jpg"
            input "selectedESP", "enum", required:false, title:"Select ESP8266 (${devicesForDialog.size() ?: 0} found)", multiple:true, options:devicesForDialog
        }
    }
}

/* Generate the list of devices for the preferences dialog */
def getDevicesForDialog() {
    def devices = getDevices()
    def map = [:]
    devices.each {
        def value = "ESP8266 (" + convertHexToIP(it.value.networkAddress) + ')'
        def key = it.value.ssdpUSN.toString()
        map["${key}"] = value
    }
    map
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    initialize()
}

def initialize() {
    log.debug('Initializing')
    unsubscribe()
    addDevices()
    unschedule()
    runEvery5Minutes("subscribeToDevices")
    subscribeToDevices()
}

/* Get map containing discovered devices. Maps USN to parsed event. */
def getDevices() {
    if (!state.devices) { state.devices = [:] }
    log.debug("There are ${state.devices.size()} devices at this time")
    state.devices
}

void ssdpDiscover() {
	log.trace "Lan discovery ${searchTarget()}"
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery ${searchTarget()}", physicalgraph.device.Protocol.LAN))
}

void ssdpSubscribe() {
	subscribe(location, "ssdpTerm.${searchTarget()}", ssdpHandler)
}

def ssdpHandler(evt) {
    if(evt.name == "ping") { return "" }
    log.debug('Received Response: ' + evt.description)
    def description = evt.description
    def hub = evt?.hubId
	def parsedEvent = parseLanMessage(description)
    parsedEvent << ["hub":hub]
    if (parsedEvent?.ssdpTerm?.contains("${searchTarget()}")) {
        def devices = getDevices()
        def ip = convertHexToIP(parsedEvent.networkAddress)
        def port = convertHexToInt(parsedEvent.deviceAddress)
		if (!(devices."${parsedEvent.ssdpUSN.toString()}")) { //if it doesn't already exist
            //log.debug('Parsed Event: ' + parsedEvent)
            devices << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
        } else { // just update the values
            def d = devices."${parsedEvent.ssdpUSN.toString()}"
            boolean deviceChangedValues = false
            if(d.ip != ip || d.port != port) {
                d.ip = ip
                d.port = port
                deviceChangedValues = true
            }
            if (deviceChangedValues) {
                def children = getChildDevices()
                children.each {
                    if (it.getDeviceDataByName("ssdpUSN") == parsedEvent.ssdpUSN) {
                        it.updateDataValue("ip", ip)
                        it.updateDataValue("port", port)
                    }
                }
            }
        }
    }
}

def addDevices() {
    selectedESP.each { ssdpUSN ->
        def devices = getDevices()

        // Make the dni the MAC followed by the index from the USN, unless it's the USN ending in :1
        // that device has just the MAC address as its DNI and receives all the notifications from
        // the device

        def dni = devices[ssdpUSN].mac + ':' + ssdpUSN.split(':').last()

        if (ssdpUSN.endsWith(":1")) { dni = devices[ssdpUSN].mac }

        // Check if child already exists
        def d = getChildDevices()?.find { it.device.deviceNetworkId == dni }

        if (!d) {
            def ip = convertHexToIP(devices[ssdpUSN].networkAddress).toString()
            def port = convertHexToInt(devices[ssdpUSN].deviceAddress).toString()
            log.debug("Adding ${dni} for ${ssdpUSN} / ${ip}:${port}")
            d = addChildDevice("trentchristopher", "ESP8266", dni, devices[ssdpUSN].hub, [
                "label": "Computer Vision Presence" ,
                "data": [
                    "ip": ip,
                    "port": port,
                    "ssdpUSN": ssdpUSN,
                    "ssdpPath": devices[ssdpUSN].ssdpPath
                ]
            ])
        } else {
        	log.debug("This device already exists")
        }
    }
}

def subscribeToDevices() {
    log.debug "subscribeToDevices() called"
    def devices = getAllChildDevices()
    devices.each { d ->
        d.subscribe()
    }
}

/* Convert hex (e.g. port number) to decimal number */
private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

/* Convert internal hex representation of IP address to dotted quad */
private String convertHexToIP(hex) {
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}