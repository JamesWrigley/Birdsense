# Birdsense
An Android app for streaming accelerometer data in realtime over a WiFi network.

The data is put onto the wire as a `float` array of size 3, with the elements
being the current *linear acceleration* (that is, not including gravity) on the
`x`, `y`, and `z` axes. The app uses port 1998 for this.
