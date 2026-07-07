package com.tinyprint.portraitstudio.prod

object StaticConfig {
    // Print Density Settings tuned for optimal darkness
    const val MOTOR_SPEED_DIVISOR = 0x1E // Slower speed (0x1E) allowing thermal head to dump more energy
    const val QUALITY_LATTICE = 0x35     // Quality Level 5 (0x35)
    const val HEATING_ENERGY = 0xFFFF    // Maximum heating energy (0xFFFF)
    const val PAPER_FEED_LINES = 80      // Feed past the cutter mechanism
}
