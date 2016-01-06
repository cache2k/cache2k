# Background refresh

 - default
 - expired entries are visible, iff a refresh task was started. if no refresh thread is available the entry is expired
 - if expiry calculator returns 0 or a time in the past, the entry is expired
 - data is kept?