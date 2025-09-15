package assettracking.manager;

import assettracking.data.bulk.BulkDevice;

import java.util.List;

public record DeviceValidationResult(List<BulkDevice> validDevices, List<String> errors) {
}