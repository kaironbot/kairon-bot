package org.wagham.exceptions

class ChannelNotFoundException(channelName: String) : Exception("Channel not found: $channelName")