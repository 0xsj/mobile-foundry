// Compile-fail: standard Result.map requires a non-throwing callback.
enum MappingDefect: Error { case unexpected }
let result: Result<Int, MappingDefect> = .success(1)
let mapped = result.map { _ -> Int in throw MappingDefect.unexpected }
