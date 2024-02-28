package io.netbird.client.tool;

import io.netbird.gomobile.android.DNSList;

interface DNSChangeListener {
    void onChanged(DNSList dnsServers) throws Exception;
}
