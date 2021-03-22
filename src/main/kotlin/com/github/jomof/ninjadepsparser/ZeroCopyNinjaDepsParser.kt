package com.github.jomof.ninjadepsparser


/**
 * Everything is aligned to four bytes, everything is little endian.
 *
 * First a 16 bytes header:
 *
 * The 12 bytes # ninjadeps\n
 * Followed an u32: The version: 3 or 4
 *
 * Then comes the data, which are records that start with a u32, of which the high bit indicates whether
 * this is a 'deps' record (1) or a path record (0), and the rest of the bits indicate the number of
 * bytes that follows the header (which is always a multiple of 4).
 *
 * A path records consists of:
 * The path itself (string) padded with zero to three NUL bytes to a 4-byte boundary.
 * Followed by an u32: The checksum, which is the binary complement of the ID.
 * The IDs of the paths are implicit: The first path in the file has ID 0, the second ID 1, etc.
 *
 * A 'deps' record consists of:
 *
 * The ID of the path for which we're listing the dependencies.
 * Followed by the mtime in nanoseconds as u64 (8 bytes) in version 4, or in seconds as u32 (4 bytes) in
 * version 3. On Windows, the year 2000 is used as the epoch instead of the Unix epoch.
 * The value 0 means 'does not exist'. 1 is used for mtimes that were actually 0.
 * Followed by the IDs of all the dependencies (paths).
 * Changes are simply appended, and later 'deps' records override earlier ones, which are considered 'dead'.
 *
 * When there are too many dead records (more than two thirds of more than 1000 records), the whole file
 * is written from scratch to 'recompact' it.
 */
class ZeroCopyNinjaDepsParser {

}