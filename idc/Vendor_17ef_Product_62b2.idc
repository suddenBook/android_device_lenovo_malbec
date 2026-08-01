# SPDX-FileCopyrightText: The Android Open Source Project
# SPDX-License-Identifier: Apache-2.0
#
# Lenovo Keyboard Pack For Yoga Tab touchpad (17ef:62b2).
#
# device.internal = 0 marks the folio as an external device, which is what
# drives AOSP's wake and rotation behaviour for it.
#
# The stock file also carries "device.lenovo_type = touchpad", a Lenovo private
# property read by their patched EventHub. AOSP's PropertyMap accepts unknown
# keys without complaint, but it is inert here, so it is left out.

device.internal = 0
