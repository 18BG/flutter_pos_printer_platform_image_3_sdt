//
//  Generated file. Do not edit.
//

// clang-format off

#import "GeneratedPluginRegistrant.h"

#if __has_include(<flutter_pos_printer_platform_image_3_sdt/FlutterPosPrinterPlatformPlugin.h>)
#import <flutter_pos_printer_platform_image_3_sdt/FlutterPosPrinterPlatformPlugin.h>
#else
@import flutter_pos_printer_platform_image_3_sdt;
#endif

#if __has_include(<network_info_plus/FPPNetworkInfoPlusPlugin.h>)
#import <network_info_plus/FPPNetworkInfoPlusPlugin.h>
#else
@import network_info_plus;
#endif

#if __has_include(<permission_handler_apple/PermissionHandlerPlugin.h>)
#import <permission_handler_apple/PermissionHandlerPlugin.h>
#else
@import permission_handler_apple;
#endif

#if __has_include(<universal_ble/UniversalBlePlugin.h>)
#import <universal_ble/UniversalBlePlugin.h>
#else
@import universal_ble;
#endif

@implementation GeneratedPluginRegistrant

+ (void)registerWithRegistry:(NSObject<FlutterPluginRegistry>*)registry {
  [FlutterPosPrinterPlatformPlugin registerWithRegistrar:[registry registrarForPlugin:@"FlutterPosPrinterPlatformPlugin"]];
  [FPPNetworkInfoPlusPlugin registerWithRegistrar:[registry registrarForPlugin:@"FPPNetworkInfoPlusPlugin"]];
  [PermissionHandlerPlugin registerWithRegistrar:[registry registrarForPlugin:@"PermissionHandlerPlugin"]];
  [UniversalBlePlugin registerWithRegistrar:[registry registrarForPlugin:@"UniversalBlePlugin"]];
}

@end
