package vn.eventplatform.event.domain.enums;

public enum InventoryUpdateStatus {
    SUCCESS,
    FAILED;

    public static InventoryUpdateStatus fromUpdatedRows(int updatedRows) {
        return updatedRows == 1 ? SUCCESS : FAILED;
    }

    public static InventoryUpdateStatus fromAffectedRows(int affectedRows) {
        return fromUpdatedRows(affectedRows);
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
