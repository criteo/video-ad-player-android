# Overview

Android sample app for showing video ad

Features implemented:
* VAST parsing
* Triggering VAST beacons
* OMID verification script and verification parameters integrated
* OMID events are sent
* 50% ad viewable in viewport is checked, autopause when less than 50% of the video is seen
* VAST click beacons, pause/unpause beacons, mute/unmute beacons



### Running this app
- NOTE: First, manually enable storage permissions : Settings -> Apps -> OMSDKSampleApp -> Permissions -> Storage
(The app may crash on recent Android versions without doing the above)
- If using emulator, try a standard & recent emulator like Pixel / API 27
  If you find that some cases don't work on emulator then you’ll need to disable any network security protection you may have enabled on your laptop (since the refApp runs a web server and if emulator is being used then your PC’s network security settings might block the refApp's web server / ports)

### Testing notes
Set up your device or emulator to proxy through Charles.

In Charles, look for urls containing: complianceomsdk.iabtechlab.com/sendmessage?
All OMID events & event data will be mapped to the above 

For native ad sessions, an external url is required to access `omid-validation-verification-script-v1`. This is specified in the 
`build.config` `VERIFICATION_URL` field.
