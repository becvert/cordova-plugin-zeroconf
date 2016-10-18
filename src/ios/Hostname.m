#import "Hostname.h"

@implementation Hostname

+ (NSString*) get
{
    char hostname[128];
    gethostname(hostname, sizeof hostname);
    return [NSString stringWithFormat:@"%s", hostname];
}

@end
