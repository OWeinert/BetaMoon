package betamoon.utils;

public interface KeyLayoutMapper {
    char map(int keyCode, boolean shift, boolean alt, boolean ctrl);
}
