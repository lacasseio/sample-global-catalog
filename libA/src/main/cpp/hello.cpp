#include <stdlib.h>
#include <sstream>
#include "libA.h"

std::string libA::version() {
    std::ostringstream oss;
    oss << "v" << VERSION;
    return oss.str();
}
