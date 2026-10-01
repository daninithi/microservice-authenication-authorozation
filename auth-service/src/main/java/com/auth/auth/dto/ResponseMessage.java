package com.auth.auth.dto;

import com.auth.auth.shared.ResponseStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseMessage<T> {
    private ResponseStatus status;
    private T data;
}
