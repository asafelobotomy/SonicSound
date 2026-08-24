package app.sonicsound.models

class ParameterException(val parameter: String): Exception("The $parameter parameter cannot be empty")