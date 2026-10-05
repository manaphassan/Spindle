package com.hana.spindle.playback.extractor

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory

/**
 * Custom Media3 [ExtractorsFactory] prioritizing native audiophile container decoders:
 * 1. [AiffExtractor]: Pure bit-perfect AIFF/AIFC IFF container parser with 80-bit float sample rate resolution.
 * 2. [DsfExtractor]: 1-bit DSD to 24-bit 88.2kHz FIR decimator with 32-tap Hann filter and LUT acceleration.
 * 3. Media3 default extractors (FLAC, WAV, MP3, OGG, AAC, etc.)
 */
@UnstableApi
class SpindleExtractorsFactory : ExtractorsFactory {

    private val defaultExtractorsFactory = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> {
        val defaultExtractors = defaultExtractorsFactory.createExtractors()
        return arrayOf(AiffExtractor(), DsfExtractor(), *defaultExtractors)
    }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> {
        val defaultExtractors = defaultExtractorsFactory.createExtractors(uri, responseHeaders)
        return arrayOf(AiffExtractor(), DsfExtractor(), *defaultExtractors)
    }
}
