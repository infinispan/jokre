package org.jboss.jokre.agent;

/**
 * Created by IntelliJ IDEA.
 * User: adinn
 * Date: 1/13/11
 * Time: 9:48 AM
 * To change this template use File | Settings | File Templates.
 */
public class InvalidNotifyException extends RuntimeException
{
    public InvalidNotifyException(String message)
    {
        super(message);
    }
}
