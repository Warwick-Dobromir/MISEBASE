package com.culinary.kitchen.archive

import com.culinary.core.data.kitchen.KitchenReferenceType

const val KITCHEN_REFERENCE_KIND_ARG: String = "kind"
const val KITCHEN_REFERENCE_ID_ARG: String = "id"

const val KITCHEN_REFERENCE_NEW_ROUTE: String =
    "kitchen/reference/new/{$KITCHEN_REFERENCE_KIND_ARG}"
const val KITCHEN_REFERENCE_DETAIL_ROUTE: String =
    "kitchen/reference/{$KITCHEN_REFERENCE_KIND_ARG}/{$KITCHEN_REFERENCE_ID_ARG}"
const val KITCHEN_REFERENCE_EDIT_ROUTE: String =
    "kitchen/reference/{$KITCHEN_REFERENCE_KIND_ARG}/{$KITCHEN_REFERENCE_ID_ARG}/edit"

fun kitchenReferenceNewRoute(type: KitchenReferenceType): String = "kitchen/reference/new/${type.routeValue()}"

fun kitchenReferenceDetailRoute(
    type: KitchenReferenceType,
    id: String,
): String = "kitchen/reference/${type.routeValue()}/$id"

fun kitchenReferenceEditRoute(
    type: KitchenReferenceType,
    id: String,
): String = "kitchen/reference/${type.routeValue()}/$id/edit"

fun kitchenReferenceTypeFromRoute(value: String?): KitchenReferenceType? =
    KitchenReferenceType.entries.firstOrNull { it.routeValue() == value }

fun KitchenReferenceType.routeValue(): String =
    when (this) {
        KitchenReferenceType.TECHNIQUE -> "technique"
        KitchenReferenceType.PRODUCTION_STANDARD -> "production-standard"
        KitchenReferenceType.STORAGE_STANDARD -> "storage-standard"
    }
