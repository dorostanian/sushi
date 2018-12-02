package nl.dorost.flow


class NonUniqueIdException(msg: String) : RuntimeException(msg)
class NonUniqueTypeException(msg: String) : RuntimeException(msg)
class MissingFieldException(msg: String) : RuntimeException(msg)

class InvalidNextIdException(msg: String) : RuntimeException(msg)
class TypeNotRegisteredException(msg: String) : RuntimeException(msg)
class ExpectedParamNotPresentException(msg: String) : RuntimeException(msg)
class MissingMappingValueException(msg: String) : RuntimeException(msg)